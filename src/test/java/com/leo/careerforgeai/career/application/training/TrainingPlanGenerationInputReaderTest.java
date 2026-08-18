package com.leo.careerforgeai.career.application.training;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.application.skillgap.DeterministicSkillGapMatcher;
import com.leo.careerforgeai.career.application.training.TrainingPlanGenerationException;
import com.leo.careerforgeai.career.application.training.TrainingPlanGenerationInputReader;
import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.document.SourceDocument;
import com.leo.careerforgeai.knowledge.infrastructure.document.loading.MarkdownDocumentLoader;
import com.leo.careerforgeai.memory.application.profile.ConfirmedSkillProfile;
import com.leo.careerforgeai.memory.application.profile.MemoryProfileQueryApplicationService;
import com.leo.careerforgeai.memory.domain.profile.*;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.testsupport.MutableCurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.leo.careerforgeai.career.application.training.TrainingPlanGenerationException.ErrorType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description: 验证训练计划固定输入的owner、版本、时间约束和资源白名单
 * @author: Miao Zheng
 * @date: 2026-08-17
 */
class TrainingPlanGenerationInputReaderTest {

    private static final ActorId ACTOR =
            new ActorId("actor-training");
    private static final UUID TARGET_ROLE_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW =
            Instant.parse("2026-08-17T06:00:00Z");

    private CareerPlanningRepository repository;
    private MemoryProfileQueryApplicationService profileQueryService;
    private MarkdownDocumentLoader documentLoader;
    private TrainingPlanGenerationInputReader reader;

    @BeforeEach
    void setUp() {
        repository = mock(CareerPlanningRepository.class);
        profileQueryService = mock(
                MemoryProfileQueryApplicationService.class
        );
        documentLoader = mock(MarkdownDocumentLoader.class);
        reader = new TrainingPlanGenerationInputReader(
                new MutableCurrentActorProvider(ACTOR),
                repository,
                profileQueryService,
                documentLoader
        );
    }

    @Test
    void shouldReadFixedVersionedInput() {
        stubBaseInput("我每周可以学习10小时");

        var input = reader.read(SNAPSHOT_ID);

        assertThat(input.ownerId()).isEqualTo(ACTOR);
        assertThat(input.weeklyAvailableMinutes()).isEqualTo(600);
        assertThat(input.gapSnapshot().snapshotId())
                .isEqualTo(SNAPSHOT_ID);
        assertThat(input.memoryRefs()).hasSize(2);
        assertThat(input.resourceRefs()).singleElement()
                .satisfies(resource -> {
                    assertThat(resource.resourceId())
                            .isEqualTo("document-1");
                    assertThat(resource.sourceHash())
                            .isEqualTo("d".repeat(64));
                });
    }

    @Test
    void shouldReportMissingSnapshot() {
        when(repository.findSkillGapSnapshot(
                ACTOR,
                SNAPSHOT_ID
        )).thenReturn(Optional.empty());

        assertFailure(
                () -> reader.read(SNAPSHOT_ID),
                GAP_SNAPSHOT_NOT_FOUND
        );

        verifyNoInteractions(
                profileQueryService,
                documentLoader
        );
    }

    @Test
    void shouldRejectChangedSkillProfileVersion() {
        stubTargetAndSnapshot();
        when(profileQueryService.findConfirmedSkillProfile())
                .thenReturn(new ConfirmedSkillProfile(
                        ACTOR,
                        3,
                        List.of()
                ));

        assertFailure(
                () -> reader.read(SNAPSHOT_ID),
                INPUT_VERSION_CONFLICT
        );

        verifyNoInteractions(documentLoader);
    }

    @Test
    void shouldRejectMissingWeeklyHours() {
        stubTargetAndSnapshot();
        when(profileQueryService.findConfirmedSkillProfile())
                .thenReturn(new ConfirmedSkillProfile(
                        ACTOR,
                        2,
                        List.of()
                ));
        when(profileQueryService.findConfirmedPlanningMemories())
                .thenReturn(List.of(confirmedMemory(
                        MemoryType.LEARNING_PREFERENCE,
                        MemoryNormalizedKey.learningPreference(
                                LearningPreferenceKey.CONTENT_FORMAT
                        ),
                        "我喜欢项目驱动学习"
                )));

        assertFailure(
                () -> reader.read(SNAPSHOT_ID),
                TIME_CONSTRAINT_MISSING
        );

        verifyNoInteractions(documentLoader);
    }

    @Test
    void shouldRejectAmbiguousWeeklyHours() {
        stubBaseInput("我每周可以学习5到10小时");

        assertFailure(
                () -> reader.read(SNAPSHOT_ID),
                TIME_CONSTRAINT_INVALID
        );

        verifyNoInteractions(documentLoader);
    }

    @Test
    void shouldRejectDuplicateControlledResourceId() {
        stubBaseInput("我每周可以学习10小时");
        when(documentLoader.loadAll()).thenReturn(List.of(
                sourceDocument(
                        "document-1",
                        KnowledgeDocumentType.JOB_DESCRIPTION
                ),
                sourceDocument(
                        "document-1",
                        KnowledgeDocumentType.INTERVIEW_EXPERIENCE
                )
        ));

        assertFailure(
                () -> reader.read(SNAPSHOT_ID),
                CONTROLLED_RESOURCE_INVALID
        );
    }

    private void stubBaseInput(String weeklyContent) {
        stubTargetAndSnapshot();
        when(profileQueryService.findConfirmedSkillProfile())
                .thenReturn(new ConfirmedSkillProfile(
                        ACTOR,
                        2,
                        List.of()
                ));
        when(profileQueryService.findConfirmedPlanningMemories())
                .thenReturn(List.of(
                        confirmedMemory(
                                MemoryType.TIME_CONSTRAINT,
                                MemoryNormalizedKey.timeConstraint(
                                        TimeConstraintKey.WEEKLY_HOURS
                                ),
                                weeklyContent
                        ),
                        confirmedMemory(
                                MemoryType.LEARNING_PREFERENCE,
                                MemoryNormalizedKey.learningPreference(
                                        LearningPreferenceKey.CONTENT_FORMAT
                                ),
                                "我喜欢项目驱动学习"
                        )
                ));
        when(documentLoader.loadAll()).thenReturn(List.of(
                sourceDocument(
                        "document-1",
                        KnowledgeDocumentType.JOB_DESCRIPTION
                )
        ));
    }

    private void stubTargetAndSnapshot() {
        TargetRole targetRole = targetRole();
        when(repository.findSkillGapSnapshot(
                ACTOR,
                SNAPSHOT_ID
        )).thenReturn(Optional.of(snapshot()));
        when(repository.findTargetRole(
                ACTOR,
                TARGET_ROLE_ID
        )).thenReturn(Optional.of(targetRole));
        when(repository.findLatestTargetRole(ACTOR))
                .thenReturn(Optional.of(targetRole));
    }

    private TargetRole targetRole() {
        return TargetRole.createConfirmed(
                TARGET_ROLE_ID,
                ACTOR,
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
    }

    private SkillGapSnapshot snapshot() {
        return SkillGapSnapshot.create(
                SNAPSHOT_ID,
                ACTOR,
                TARGET_ROLE_ID,
                2,
                2,
                DeterministicSkillGapMatcher.ALGORITHM_VERSION,
                List.of(new SkillGapSnapshot.GapItem(
                        UUID.fromString(
                                "30000000-0000-0000-0000-000000000001"
                        ),
                        "programmingLanguages[0]",
                        "Java",
                        SkillGapSnapshot.GapStatus.MISSING,
                        List.of(),
                        "当前画像中没有Java证据"
                )),
                NOW.minusSeconds(60)
        );
    }

    private MemoryItem confirmedMemory(
            MemoryType type,
            MemoryNormalizedKey normalizedKey,
            String content
    ) {
        UUID memoryId = UUID.randomUUID();
        MemoryItem pending = MemoryItem.createPending(
                memoryId,
                ACTOR,
                type,
                normalizedKey,
                content,
                new MemorySource(
                        MemorySourceType.CONVERSATION_TURN,
                        "turn-" + memoryId,
                        "c".repeat(64)
                ),
                List.of("turn-" + memoryId),
                NOW.minusSeconds(40)
        );
        return pending.applyDecision(MemoryDecision.create(
                UUID.randomUUID(),
                pending,
                ACTOR,
                MemoryDecisionType.CONFIRM,
                null,
                "用户确认计划输入",
                NOW.minusSeconds(30)
        ));
    }

    private SourceDocument sourceDocument(
            String documentId,
            KnowledgeDocumentType type
    ) {
        return new SourceDocument(
                "careerforge",
                documentId,
                "训练资料",
                type,
                "training.md",
                "d".repeat(64),
                "受控文档正文"
        );
    }

    private void assertFailure(
            ThrowingRunnable invocation,
            TrainingPlanGenerationException.ErrorType errorType
    ) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(
                        TrainingPlanGenerationException.class,
                        exception -> assertThat(
                                exception.getErrorType()
                        ).isEqualTo(errorType)
                );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}