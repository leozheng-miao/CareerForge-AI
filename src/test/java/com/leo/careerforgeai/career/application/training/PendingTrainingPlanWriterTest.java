package com.leo.careerforgeai.career.application.training;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.application.skillgap.DeterministicSkillGapMatcher;
import com.leo.careerforgeai.career.domain.*;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.memory.application.profile.ConfirmedSkillProfile;
import com.leo.careerforgeai.memory.domain.profile.*;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.leo.careerforgeai.career.application.training.TrainingPlanGenerationException.ErrorType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description: 验证待确认训练计划的最终输入复核、版本分配、状态和持久化失败语义
 * @author: Miao Zheng
 * @date: 2026-08-18
 */
@ExtendWith(MockitoExtension.class)
class PendingTrainingPlanWriterTest {

    private static final ActorId ACTOR_A = new ActorId("actor-training-a");
    private static final ActorId ACTOR_B = new ActorId("actor-training-b");
    private static final UUID TARGET_ROLE_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID GAP_ITEM_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW =
            Instant.parse("2026-08-18T04:00:00Z");

    @Mock
    private TrainingPlanGenerationInputReader inputReader;

    @Mock
    private CareerPlanningRepository repository;

    private PendingTrainingPlanWriter writer;
    private TrainingPlanGenerationInputReader.FixedInput input;
    private TrainingPlanGenerator.GeneratedPlan generatedPlan;

    @BeforeEach
    void setUp() {
        writer = new PendingTrainingPlanWriter(
                inputReader,
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        input = fixedInput();
        generatedPlan = generatedPlan(draftItem());
    }

    @Test
    void shouldPersistPendingConfirmationPlan() {
        when(inputReader.read(SNAPSHOT_ID)).thenReturn(input);
        when(repository.findLatestTrainingPlan(ACTOR_A)).thenReturn(Optional.empty());

        TrainingPlan result = writer.save(input, generatedPlan);

        assertThat(result.planId()).isNotNull();
        assertThat(result.ownerId()).isEqualTo(ACTOR_A);
        assertThat(result.planVersion()).isEqualTo(1);
        assertThat(result.gapSnapshotId()).isEqualTo(SNAPSHOT_ID);
        assertThat(result.title()).isEqualTo("Java后端训练计划");
        assertThat(result.status()).isEqualTo(TrainingPlan.PlanStatus.PENDING_CONFIRMATION);
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.updatedAt()).isEqualTo(NOW);
        assertThat(result.items()).containsExactlyElementsOf(generatedPlan.items());
        assertThat(result.items()).allMatch(item ->
                item.status() == TrainingPlanItem.ItemStatus.NOT_STARTED
                        && item.completionEvidenceRefs().isEmpty()
        );

        TrainingPlan.GenerationContext context = result.generationContext();
        assertThat(context.schemaVersion())
                .isEqualTo(PendingTrainingPlanWriter.CONTEXT_SCHEMA_VERSION);
        assertThat(context.inputPolicyVersion())
                .isEqualTo(TrainingPlanGenerationInputReader.INPUT_POLICY_VERSION);
        assertThat(context.weeklyAvailableMinutes()).isEqualTo(600);
        assertThat(context.memoryRefs()).containsExactlyElementsOf(input.memoryRefs());
        assertThat(context.allowedResources()).containsExactlyElementsOf(input.resourceRefs());
        assertThat(context.generatorVersion()).isEqualTo(TrainingPlanGenerator.GENERATOR_VERSION);
        assertThat(context.promptVersion()).isEqualTo(TrainingPlanGenerator.PROMPT_VERSION);
        assertThat(context.modelRequestId()).isEqualTo("model-request-1");
        assertThat(context.inputTokens()).isEqualTo(100);
        assertThat(context.outputTokens()).isEqualTo(50);
        assertThat(context.totalTokens()).isEqualTo(150);
        assertThat(context.modelDurationMs()).isEqualTo(25);

        verify(inputReader).read(SNAPSHOT_ID);
        verify(repository).findLatestTrainingPlan(ACTOR_A);
        verify(repository).insertTrainingPlan(result);
    }

    @Test
    void shouldAllocateNextOwnerPlanVersion() {
        when(inputReader.read(SNAPSHOT_ID)).thenReturn(input);
        when(repository.findLatestTrainingPlan(ACTOR_A))
                .thenReturn(Optional.of(historicalPlan(ACTOR_A, 4)));

        TrainingPlan result = writer.save(input, generatedPlan);

        assertThat(result.planVersion()).isEqualTo(5);
        assertThat(result.status()).isEqualTo(TrainingPlan.PlanStatus.PENDING_CONFIRMATION);
        verify(repository).insertTrainingPlan(result);
    }

    @Test
    void shouldRejectChangedInputAfterModelCall() {
        TrainingPlanGenerationInputReader.FixedInput changedInput =
                new TrainingPlanGenerationInputReader.FixedInput(
                        input.inputPolicyVersion(),
                        input.ownerId(),
                        input.targetRole(),
                        input.gapSnapshot(),
                        input.skillProfile(),
                        300,
                        input.planningMemories(),
                        input.controlledResources()
                );
        when(inputReader.read(SNAPSHOT_ID)).thenReturn(changedInput);

        assertThatThrownBy(() -> writer.save(input, generatedPlan))
                .isInstanceOfSatisfying(
                        TrainingPlanGenerationException.class,
                        exception -> assertThat(exception.getErrorType())
                                .isEqualTo(INPUT_VERSION_CONFLICT)
                )
                .hasMessage("训练计划生成期间输入已经变化，请重新生成");

        verify(inputReader).read(SNAPSHOT_ID);
        verify(repository).findTrainingPlan(eq(ACTOR_A), any(UUID.class));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldRejectGeneratedCompletedItem() {
        TrainingPlanItem completedItem = draftItem()
                .start(NOW.minusSeconds(6))
                .complete(List.of("github:commit/project-evidence-1"), NOW.minusSeconds(5));

        TrainingPlanGenerator.GeneratedPlan invalidPlan =
                generatedPlan(completedItem);

        when(inputReader.read(SNAPSHOT_ID)).thenReturn(input);

        assertThatThrownBy(() -> writer.save(input, invalidPlan))
                .isInstanceOfSatisfying(
                        TrainingPlanGenerationException.class,
                        exception -> assertThat(exception.getErrorType())
                                .isEqualTo(MODEL_OUTPUT_INVALID)
                )
                .hasMessage("待确认计划项不能包含进度或完成证据");

        verify(repository).findTrainingPlan(eq(ACTOR_A), any(UUID.class));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldRejectCrossOwnerLatestPlan() {
        when(inputReader.read(SNAPSHOT_ID)).thenReturn(input);
        when(repository.findLatestTrainingPlan(ACTOR_A))
                .thenReturn(Optional.of(historicalPlan(ACTOR_B, 3)));

        assertThatThrownBy(() -> writer.save(input, generatedPlan))
                .isInstanceOfSatisfying(
                        TrainingPlanGenerationException.class,
                        exception -> assertThat(exception.getErrorType())
                                .isEqualTo(INPUT_INTEGRITY_VIOLATION)
                )
                .hasMessage("最新训练计划违反owner边界");

        verify(repository, never()).insertTrainingPlan(any());
    }

    @Test
    void shouldMapRepositoryFailureToPersistenceFailure() {
        when(inputReader.read(SNAPSHOT_ID)).thenReturn(input);
        when(repository.findLatestTrainingPlan(ACTOR_A)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("insert failed"))
                .when(repository).insertTrainingPlan(any());

        assertThatThrownBy(() -> writer.save(input, generatedPlan))
                .isInstanceOfSatisfying(
                        TrainingPlanGenerationException.class,
                        exception -> {
                            assertThat(exception.getErrorType()).isEqualTo(PERSISTENCE_FAILED);
                            assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
                        }
                )
                .hasMessage("训练计划持久化失败");
    }

    @Test
    void shouldPersistExactlyTheReturnedPlan() {
        when(inputReader.read(SNAPSHOT_ID)).thenReturn(input);
        when(repository.findLatestTrainingPlan(ACTOR_A)).thenReturn(Optional.empty());

        TrainingPlan result = writer.save(input, generatedPlan);

        ArgumentCaptor<TrainingPlan> captor = ArgumentCaptor.forClass(TrainingPlan.class);
        verify(repository).insertTrainingPlan(captor.capture());
        assertThat(captor.getValue()).isSameAs(result);
    }

    private TrainingPlanGenerationInputReader.FixedInput fixedInput() {
        TargetRole targetRole = TargetRole.createConfirmed(
                TARGET_ROLE_ID,
                ACTOR_A,
                2,
                "job-description-1",
                "a".repeat(64),
                "job-requirements-parser-v1",
                "job-requirements-prompt-v1",
                new JobRequirements(
                        "Java后端工程师",
                        List.of("Java"),
                        List.of("Spring Boot"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                NOW.minusSeconds(120)
        );

        SkillGapSnapshot snapshot = SkillGapSnapshot.create(
                SNAPSHOT_ID,
                ACTOR_A,
                TARGET_ROLE_ID,
                2,
                0,
                DeterministicSkillGapMatcher.ALGORITHM_VERSION,
                List.of(new SkillGapSnapshot.GapItem(
                        GAP_ITEM_ID,
                        "programmingLanguages[0]",
                        "Java",
                        SkillGapSnapshot.GapStatus.MISSING,
                        List.of(),
                        "当前画像中没有Java证据"
                )),
                NOW.minusSeconds(90)
        );

        MemoryItem weeklyHours = confirmedWeeklyHours();
        var resource = new TrainingPlanGenerationInputReader.ControlledResource(
                "careerforge",
                "document-1",
                "Java训练资料",
                KnowledgeDocumentType.JOB_DESCRIPTION,
                "d".repeat(64)
        );

        return new TrainingPlanGenerationInputReader.FixedInput(
                TrainingPlanGenerationInputReader.INPUT_POLICY_VERSION,
                ACTOR_A,
                targetRole,
                snapshot,
                new ConfirmedSkillProfile(ACTOR_A, 0, List.of()),
                600,
                List.of(weeklyHours),
                List.of(resource)
        );
    }

    private MemoryItem confirmedWeeklyHours() {
        UUID memoryId =
                UUID.fromString("50000000-0000-0000-0000-000000000001");
        MemoryItem pending = MemoryItem.createPending(
                memoryId,
                ACTOR_A,
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(TimeConstraintKey.WEEKLY_HOURS),
                "我每周可以学习10小时",
                new MemorySource(
                        MemorySourceType.CONVERSATION_TURN,
                        "turn-weekly-hours",
                        "c".repeat(64)
                ),
                List.of("turn-weekly-hours"),
                NOW.minusSeconds(60)
        );
        return pending.applyDecision(MemoryDecision.create(
                UUID.fromString("60000000-0000-0000-0000-000000000001"),
                pending,
                ACTOR_A,
                MemoryDecisionType.CONFIRM,
                null,
                "用户确认每周时间",
                NOW.minusSeconds(50)
        ));
    }

    private TrainingPlanItem draftItem() {
        return TrainingPlanItem.createDraft(
                ITEM_ID,
                1,
                "完成Java并发训练",
                "实现并验证一个线程安全的任务处理器",
                120,
                "自动化测试覆盖并发成功和冲突场景",
                "提交代码仓库引用和测试结果",
                List.of(GAP_ITEM_ID),
                null,
                List.of(new TrainingPlanItem.ResourceRef(
                        TrainingPlanItem.ResourceType.KNOWLEDGE_DOCUMENT,
                        "document-1"
                )),
                NOW.minusSeconds(10)
        );
    }

    private TrainingPlanGenerator.GeneratedPlan generatedPlan(
            TrainingPlanItem item
    ) {
        return new TrainingPlanGenerator.GeneratedPlan(
                "Java后端训练计划",
                1,
                List.of(item),
                "model-request-1",
                new ModelUsage(100, 50, 150),
                25
        );
    }

    private TrainingPlan historicalPlan(
            ActorId ownerId,
            long planVersion
    ) {
        Instant createdAt = NOW.minusSeconds(300);
        TrainingPlanItem item = TrainingPlanItem.createDraft(
                UUID.randomUUID(),
                1,
                "历史训练任务",
                "完成历史训练任务",
                60,
                "提交历史任务测试结果",
                "提交历史任务证据",
                List.of(GAP_ITEM_ID),
                null,
                List.of(),
                createdAt
        );

        return TrainingPlan.createDraft(
                UUID.randomUUID(),
                ownerId,
                planVersion,
                SNAPSHOT_ID,
                "历史训练计划",
                List.of(item),
                createdAt
        ).submitForConfirmation(createdAt.plusSeconds(1));
    }
}