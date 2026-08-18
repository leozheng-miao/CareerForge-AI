package com.leo.careerforgeai.career.application.training;

import com.leo.careerforgeai.career.application.skillgap.DeterministicSkillGapMatcher;
import com.leo.careerforgeai.career.domain.*;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.memory.application.profile.ConfirmedSkillProfile;
import com.leo.careerforgeai.memory.domain.profile.*;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.domain.*;
import com.leo.careerforgeai.shared.actor.ActorId;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.leo.careerforgeai.career.application.training.TrainingPlanGenerationException.ErrorType.MODEL_OUTPUT_INVALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description: 验证训练计划模型输出的结构、Gap、资源、时间、重复任务和事实边界
 * @author: Miao Zheng
 * @date: 2026-08-18
 */
class TrainingPlanGeneratorTest {

    private static final ActorId ACTOR_ID = new ActorId("actor-training-generator");
    private static final UUID TARGET_ROLE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SNAPSHOT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID GAP_ITEM_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID UNKNOWN_GAP_ITEM_ID = UUID.fromString("30000000-0000-0000-0000-000000000099");
    private static final Instant NOW = Instant.parse("2026-08-18T04:00:00Z");

    private final ModelGateway modelGateway = mock(ModelGateway.class);
    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private final TrainingPlanGenerator generator = new TrainingPlanGenerator(
            modelGateway,
            JsonMapper.builder().build(),
            validatorFactory.getValidator(),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );
    private final TrainingPlanGenerationInputReader.FixedInput input = fixedInput();

    @AfterEach
    void closeResources() {
        validatorFactory.close();
    }

    @Test
    void shouldGenerateValidatedDraftItems() {
        when(modelGateway.chat(any())).thenReturn(response(validOutput()));

        TrainingPlanGenerator.GeneratedPlan result = generator.generate(input);

        assertThat(result.title()).isEqualTo("Java后端训练计划");
        assertThat(result.durationWeeks()).isEqualTo(1);
        assertThat(result.modelRequestId()).isEqualTo("training-model-request-1");
        assertThat(result.modelUsage()).isEqualTo(new ModelUsage(100, 50, 150));
        assertThat(result.items()).hasSize(1);

        TrainingPlanItem item = result.items().getFirst();
        assertThat(item.status()).isEqualTo(TrainingPlanItem.ItemStatus.NOT_STARTED);
        assertThat(item.weekNumber()).isEqualTo(1);
        assertThat(item.gapItemIds()).containsExactly(GAP_ITEM_ID);
        assertThat(item.resourceRefs()).containsExactly(new TrainingPlanItem.ResourceRef(
                TrainingPlanItem.ResourceType.KNOWLEDGE_DOCUMENT,
                "document-1"
        ));
        assertThat(item.completionEvidenceRefs()).isEmpty();

        ArgumentCaptor<ModelRequest> requestCaptor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(modelGateway).chat(requestCaptor.capture());
        assertThat(requestCaptor.getValue().outputFormat()).isEqualTo(ModelOutputFormat.JSON_OBJECT);
        assertThat(requestCaptor.getValue().messages()).extracting(ModelMessage::role)
                .containsExactly(ModelRole.SYSTEM, ModelRole.USER);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("invalidOutputs")
    void shouldRejectUntrustedModelOutput(String expectedMessage, String output) {
        when(modelGateway.chat(any())).thenReturn(response(output));

        assertThatThrownBy(() -> generator.generate(input))
                .isInstanceOfSatisfying(TrainingPlanGenerationException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(MODEL_OUTPUT_INVALID))
                .hasMessage(expectedMessage);

        verify(modelGateway).chat(any(ModelRequest.class));
    }

    private static Stream<Arguments> invalidOutputs() {
        String validItem = itemJson(1, "完成Java并发训练", "实现并验证线程安全任务处理器",
                120, GAP_ITEM_ID, "document-1");
        return Stream.of(
                arguments("训练计划结构化输出不是合法JSON", "{"),
                arguments("任务引用了输入白名单之外的Gap",
                        planJson(1, itemJson(1, "未知Gap任务", "完成受控训练任务",
                                120, UNKNOWN_GAP_ITEM_ID, "document-1"))),
                arguments("任务引用了输入白名单之外的资源",
                        planJson(1, itemJson(1, "未知资源任务", "完成受控训练任务",
                                120, GAP_ITEM_ID, "unknown-document"))),
                arguments("每周任务时长超过用户已确认的可用时间",
                        planJson(1, itemJson(1, "超时训练任务", "完成超出预算的训练任务",
                                601, GAP_ITEM_ID, "document-1"))),
                arguments("训练计划包含重复任务", planJson(1, validItem, validItem)),
                arguments("计划周期内每周必须至少包含一个任务", planJson(2, validItem)),
                arguments("训练计划包含未经输入支持的用户事实陈述",
                        planJson(1, itemJson(1, "Java训练任务", "你已经掌握Java并发编程",
                                120, GAP_ITEM_ID, "document-1")))
        );
    }

    private static String validOutput() {
        return planJson(1, itemJson(1, "完成Java并发训练", "实现并验证线程安全任务处理器",
                120, GAP_ITEM_ID, "document-1"));
    }

    private static String planJson(int durationWeeks, String... items) {
        return """
                {
                  "title": "Java后端训练计划",
                  "durationWeeks": %d,
                  "items": [%s]
                }
                """.formatted(durationWeeks, String.join(",", items));
    }

    private static String itemJson(
            int weekNumber,
            String title,
            String taskDescription,
            int estimatedMinutes,
            UUID gapItemId,
            String resourceId
    ) {
        return """
                {
                  "weekNumber": %d,
                  "title": "%s",
                  "taskDescription": "%s",
                  "estimatedMinutes": %d,
                  "completionCriteria": "代码和自动化测试通过",
                  "evidenceRequirement": "提交代码仓库引用和测试报告",
                  "gapItemIds": ["%s"],
                  "foundationGoal": null,
                  "resourceRefs": [{
                    "resourceType": "KNOWLEDGE_DOCUMENT",
                    "resourceId": "%s"
                  }]
                }
                """.formatted(weekNumber, title, taskDescription, estimatedMinutes, gapItemId, resourceId);
    }

    private static ModelResponse response(String content) {
        return new ModelResponse(
                "training-model-request-1",
                "deepseek-v4-flash",
                content,
                new ModelUsage(100, 50, 150)
        );
    }

    private static TrainingPlanGenerationInputReader.FixedInput fixedInput() {
        TargetRole targetRole = TargetRole.createConfirmed(
                TARGET_ROLE_ID,
                ACTOR_ID,
                1,
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
                ACTOR_ID,
                TARGET_ROLE_ID,
                1,
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
        return new TrainingPlanGenerationInputReader.FixedInput(
                TrainingPlanGenerationInputReader.INPUT_POLICY_VERSION,
                ACTOR_ID,
                targetRole,
                snapshot,
                new ConfirmedSkillProfile(ACTOR_ID, 0, List.of()),
                600,
                List.of(confirmedWeeklyHours()),
                List.of(new TrainingPlanGenerationInputReader.ControlledResource(
                        "careerforge",
                        "document-1",
                        "Java训练资料",
                        KnowledgeDocumentType.JOB_DESCRIPTION,
                        "d".repeat(64)
                ))
        );
    }

    private static MemoryItem confirmedWeeklyHours() {
        MemoryItem pending = MemoryItem.createPending(
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                ACTOR_ID,
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
                ACTOR_ID,
                MemoryDecisionType.CONFIRM,
                null,
                "用户确认每周时间",
                NOW.minusSeconds(50)
        ));
    }
}