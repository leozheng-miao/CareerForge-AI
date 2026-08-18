package com.leo.careerforgeai.career.evaluation;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.application.skillgap.DeterministicSkillGapMatcher;
import com.leo.careerforgeai.career.application.skillgap.SkillGapSnapshotApplicationService;
import com.leo.careerforgeai.career.application.training.*;
import com.leo.careerforgeai.career.domain.*;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.memory.application.profile.ConfirmedSkillProfile;
import com.leo.careerforgeai.memory.application.profile.MemoryProfileQueryApplicationService;
import com.leo.careerforgeai.memory.domain.profile.*;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.testsupport.MutableCurrentActorProvider;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description: 使用固定Case评测训练计划的确认门槛、模型白名单、证据覆盖和历史版本
 * @author: Miao Zheng
 * @date: 2026-08-18
 */
class TrainingPlanFixedEvaluationTest {

    private static final String DATASET_RESOURCE =
            "training/evaluation/training-plan-cases.json";
    private static final ActorId ACTOR = new ActorId("training-evaluation-actor");
    private static final UUID TARGET_ROLE_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID GAP_ITEM_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-18T06:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldEvaluateFixedTrainingPlanCasesAndReportMetrics() throws Exception {
        EvaluationDataset dataset = loadDataset();
        int taskSuccess = 0;
        int confirmationApplicable = 0;
        int confirmationCorrect = 0;
        int evidenceCoveredItems = 0;
        int acceptedItems = 0;
        int legalRawResourceRefs = 0;
        int totalRawResourceRefs = 0;
        int conflictApplicable = 0;
        int conflictCorrect = 0;
        int stubModelRuns = 0;
        int stubModelTokens = 0;

        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            for (EvaluationCase evaluationCase : dataset.cases()) {
                if ("TARGET_NOT_CONFIRMED".equals(evaluationCase.scenario())) {
                    confirmationApplicable++;
                    if (evaluateUnconfirmedTarget()) {
                        confirmationCorrect++;
                        taskSuccess++;
                    }
                    continue;
                }

                TrainingPlanGenerationInputReader.FixedInput input = fixedInput();
                totalRawResourceRefs++;
                if (input.controlledResources().stream()
                        .anyMatch(resource -> resource.documentId()
                                .equals(evaluationCase.resourceId()))) {
                    legalRawResourceRefs++;
                }

                ModelGateway modelGateway = mock(ModelGateway.class);
                when(modelGateway.chat(any())).thenReturn(modelResponse(
                        evaluationCase.caseId(),
                        planJson(
                                evaluationCase.estimatedMinutes(),
                                evaluationCase.resourceId()
                        )
                ));
                TrainingPlanGenerator generator = new TrainingPlanGenerator(
                        modelGateway,
                        JsonMapper.builder().build(),
                        validatorFactory.getValidator(),
                        CLOCK
                );

                TrainingPlanGenerator.GeneratedPlan generatedPlan = null;
                TrainingPlanGenerationException failure = null;
                try {
                    generatedPlan = generator.generate(input);
                } catch (TrainingPlanGenerationException exception) {
                    failure = exception;
                }
                stubModelRuns++;
                stubModelTokens += 150;

                if ("REJECTED".equals(evaluationCase.expectedOutcome())) {
                    boolean rejectedCorrectly = failure != null
                            && failure.getErrorType().name()
                            .equals(evaluationCase.expectedErrorType());
                    assertThat(rejectedCorrectly)
                            .as("%s：%s", evaluationCase.caseId(), evaluationCase.labelReason())
                            .isTrue();
                    taskSuccess++;
                    continue;
                }

                assertThat(failure).as(evaluationCase.caseId()).isNull();
                assertThat(generatedPlan).as(evaluationCase.caseId()).isNotNull();

                for (TrainingPlanItem item : generatedPlan.items()) {
                    acceptedItems++;
                    boolean covered = !item.evidenceRequirement().isBlank()
                            && ((!item.gapItemIds().isEmpty()) != (item.foundationGoal() != null))
                            && input.gapSnapshot().items().stream()
                            .map(SkillGapSnapshot.GapItem::gapItemId)
                            .toList()
                            .containsAll(item.gapItemIds());
                    if (covered) evidenceCoveredItems++;
                }

                if (!evaluationCase.persist()) {
                    taskSuccess++;
                    continue;
                }

                TrainingPlanGenerationInputReader inputReader =
                        mock(TrainingPlanGenerationInputReader.class);
                CareerPlanningRepository repository =
                        mock(CareerPlanningRepository.class);
                when(inputReader.read(SNAPSHOT_ID)).thenReturn(input);

                TrainingPlan historicalPlan = null;
                if (evaluationCase.latestPlanVersion() == 0) {
                    when(repository.findLatestTrainingPlan(ACTOR))
                            .thenReturn(Optional.empty());
                } else {
                    historicalPlan = historicalPlan(evaluationCase.latestPlanVersion());
                    when(repository.findLatestTrainingPlan(ACTOR))
                            .thenReturn(Optional.of(historicalPlan));
                }

                PendingTrainingPlanWriter writer =
                        new PendingTrainingPlanWriter(inputReader, repository, CLOCK);
                TrainingPlan saved = writer.save(input, generatedPlan);

                confirmationApplicable++;
                boolean pendingCorrectly = saved.status()
                        == TrainingPlan.PlanStatus.PENDING_CONFIRMATION
                        && saved.activatedAt() == null
                        && saved.planVersion() == evaluationCase.expectedPlanVersion();
                assertThat(pendingCorrectly)
                        .as("%s：%s", evaluationCase.caseId(), evaluationCase.labelReason())
                        .isTrue();
                confirmationCorrect++;

                verify(repository).insertTrainingPlan(saved);
                if ("TARGET_VERSION_CHANGED_NEW_PLAN".equals(evaluationCase.scenario())) {
                    conflictApplicable++;
                    boolean newVersionCreated = historicalPlan != null
                            && !historicalPlan.planId().equals(saved.planId())
                            && historicalPlan.planVersion() == 1
                            && saved.planVersion() == 2;
                    assertThat(newVersionCreated).as(evaluationCase.caseId()).isTrue();
                    conflictCorrect++;
                }
                taskSuccess++;
            }
        }

        assertThat(taskSuccess).isEqualTo(6);
        assertThat(confirmationCorrect).isEqualTo(confirmationApplicable).isEqualTo(3);
        assertThat(evidenceCoveredItems).isEqualTo(acceptedItems).isEqualTo(3);
        assertThat(legalRawResourceRefs).isEqualTo(4);
        assertThat(totalRawResourceRefs).isEqualTo(5);
        assertThat(conflictCorrect).isEqualTo(conflictApplicable).isEqualTo(1);
        assertThat(stubModelRuns).isEqualTo(5);
        assertThat(stubModelTokens).isEqualTo(750);

        System.out.printf(
                Locale.ROOT,
                """
                ================ Training Plan Fixed Evaluation ================
                Task Success Rate: %d/%d
                Confirmation Enforcement Rate: %d/%d
                Training Plan Evidence Coverage: %d/%d
                Resource Reference Legal Rate: %d/%d
                Conflict Handling Accuracy: %d/%d
                Average Stub Plan Tokens: %.1f (%d runs)
                Plan Model Latency p50/p95: N/A (Stub is not provider performance)
                =================================================================
                """,
                taskSuccess,
                dataset.cases().size(),
                confirmationCorrect,
                confirmationApplicable,
                evidenceCoveredItems,
                acceptedItems,
                legalRawResourceRefs,
                totalRawResourceRefs,
                conflictCorrect,
                conflictApplicable,
                (double) stubModelTokens / stubModelRuns,
                stubModelRuns
        );
    }

    private static boolean evaluateUnconfirmedTarget() {
        CareerPlanningRepository repository = mock(CareerPlanningRepository.class);
        MemoryProfileQueryApplicationService profileService =
                mock(MemoryProfileQueryApplicationService.class);
        when(repository.findLatestTargetRole(ACTOR)).thenReturn(Optional.empty());

        SkillGapSnapshotApplicationService service =
                new SkillGapSnapshotApplicationService(
                        new MutableCurrentActorProvider(ACTOR),
                        repository,
                        profileService,
                        new DeterministicSkillGapMatcher(),
                        CLOCK
                );

        try {
            service.generate(TARGET_ROLE_ID, 2, 0);
            return false;
        } catch (IllegalArgumentException exception) {
            boolean correct = "当前用户尚未确认目标岗位".equals(exception.getMessage());
            verifyNoInteractions(profileService);
            verify(repository, never()).insertSkillGapSnapshot(any());
            return correct;
        }
    }

    private static TrainingPlanGenerationInputReader.FixedInput fixedInput() {
        TargetRole targetRole = TargetRole.createConfirmed(
                TARGET_ROLE_ID,
                ACTOR,
                2,
                "fixed-job-description",
                "a".repeat(64),
                "job-requirements-parser-v1",
                "job-requirements-prompt-v1",
                new JobRequirements(
                        "Java后端工程师",
                        List.of("Java"),
                        List.of(),
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
                ACTOR,
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
        return new TrainingPlanGenerationInputReader.FixedInput(
                TrainingPlanGenerationInputReader.INPUT_POLICY_VERSION,
                ACTOR,
                targetRole,
                snapshot,
                new ConfirmedSkillProfile(ACTOR, 0, List.of()),
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
        String sourceId = id("weekly-hours-source").toString();
        MemoryItem pending = MemoryItem.createPending(
                id("weekly-hours-memory"),
                ACTOR,
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(TimeConstraintKey.WEEKLY_HOURS),
                "我每周可以学习10小时",
                new MemorySource(
                        MemorySourceType.CONVERSATION_TURN,
                        sourceId,
                        "c".repeat(64)
                ),
                List.of(sourceId),
                NOW.minusSeconds(60)
        );
        return pending.applyDecision(MemoryDecision.create(
                id("weekly-hours-decision"),
                pending,
                ACTOR,
                MemoryDecisionType.CONFIRM,
                null,
                "用户确认每周时间",
                NOW.minusSeconds(50)
        ));
    }

    private static TrainingPlan historicalPlan(long planVersion) {
        Instant createdAt = NOW.minusSeconds(300);
        TrainingPlanItem item = TrainingPlanItem.createDraft(
                id("historical-item-" + planVersion),
                1,
                "历史训练任务",
                "完成历史训练任务",
                60,
                "自动化测试通过",
                "提交历史任务证据",
                List.of(GAP_ITEM_ID),
                null,
                List.of(),
                createdAt
        );
        return TrainingPlan.createDraft(
                id("historical-plan-" + planVersion),
                ACTOR,
                planVersion,
                SNAPSHOT_ID,
                "历史训练计划",
                List.of(item),
                createdAt
        ).submitForConfirmation(createdAt.plusSeconds(1));
    }

    private static String planJson(int estimatedMinutes, String resourceId) {
        return """
                {
                  "title": "Java后端训练计划",
                  "durationWeeks": 1,
                  "items": [{
                    "weekNumber": 1,
                    "title": "完成Java并发训练",
                    "taskDescription": "实现并验证线程安全任务处理器",
                    "estimatedMinutes": %d,
                    "completionCriteria": "代码和自动化测试通过",
                    "evidenceRequirement": "提交代码仓库引用和测试报告",
                    "gapItemIds": ["%s"],
                    "foundationGoal": null,
                    "resourceRefs": [{
                      "resourceType": "KNOWLEDGE_DOCUMENT",
                      "resourceId": "%s"
                    }]
                  }]
                }
                """.formatted(estimatedMinutes, GAP_ITEM_ID, resourceId);
    }

    private static ModelResponse modelResponse(String caseId, String content) {
        return new ModelResponse(
                "stub-" + caseId,
                "fixed-stub-model",
                content,
                new ModelUsage(100, 50, 150)
        );
    }

    private static EvaluationDataset loadDataset() throws Exception {
        InputStream resource = TrainingPlanFixedEvaluationTest.class.getClassLoader()
                .getResourceAsStream(DATASET_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException("固定训练计划评测集不存在：" + DATASET_RESOURCE);
        }
        try (resource) {
            return JsonMapper.builder()
                    .build()
                    .readerFor(EvaluationDataset.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(resource);
        }
    }

    private static UUID id(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义训练计划固定评测集
     * @author: Miao Zheng
     * @date: 2026-08-18
     * @param schemaVersion 数据结构版本
     * @param evaluationSetVersion 固定评测集版本
     * @param cases 固定训练计划Case
     */
    private record EvaluationDataset(
            String schemaVersion,
            String evaluationSetVersion,
            List<EvaluationCase> cases
    ) {
        private EvaluationDataset {
            if (!"training-plan-evaluation-v1".equals(schemaVersion)) {
                throw new IllegalArgumentException("schemaVersion不受支持");
            }
            if (!"careerforge-training-plan-eval-v1".equals(evaluationSetVersion)) {
                throw new IllegalArgumentException("evaluationSetVersion不受支持");
            }
            if (cases == null || cases.size() != 6 || cases.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("训练计划固定评测集必须包含6条Case");
            }
            cases = List.copyOf(cases);
            if (cases.stream().map(EvaluationCase::caseId).distinct().count() != cases.size()) {
                throw new IllegalArgumentException("caseId不能重复");
            }
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义单条训练计划固定输入、预期错误和版本结果
     * @author: Miao Zheng
     * @date: 2026-08-18
     * @param caseId Case唯一ID
     * @param scenario 场景类型
     * @param estimatedMinutes 模型任务预计分钟数
     * @param resourceId 模型返回的资源ID
     * @param expectedOutcome 预期接受或拒绝
     * @param expectedErrorType 预期错误类型
     * @param persist 是否进入PENDING_CONFIRMATION持久化边界
     * @param latestPlanVersion 当前历史计划业务版本，0表示不存在
     * @param expectedPlanVersion 预期新计划业务版本
     * @param labelReason 标注依据
     */
    private record EvaluationCase(
            String caseId,
            String scenario,
            int estimatedMinutes,
            String resourceId,
            String expectedOutcome,
            String expectedErrorType,
            boolean persist,
            long latestPlanVersion,
            long expectedPlanVersion,
            String labelReason
    ) {
        private EvaluationCase {
            if (caseId == null || !caseId.matches("training-plan-eval-[0-9]{3}")) {
                throw new IllegalArgumentException("caseId格式不合法");
            }
            if (scenario == null || scenario.isBlank()) {
                throw new IllegalArgumentException("scenario不能为空");
            }
            if (!Set.of("ACCEPTED", "REJECTED").contains(expectedOutcome)) {
                throw new IllegalArgumentException("expectedOutcome不受支持");
            }
            if (estimatedMinutes < 0 || latestPlanVersion < 0 || expectedPlanVersion < 0) {
                throw new IllegalArgumentException("评测数量不能小于0");
            }
            if (!"TARGET_NOT_CONFIRMED".equals(scenario)
                    && (estimatedMinutes < 1 || resourceId == null || resourceId.isBlank())) {
                throw new IllegalArgumentException("模型Case必须包含任务时间和资源");
            }
            if ("REJECTED".equals(expectedOutcome)
                    && (expectedErrorType == null || expectedErrorType.isBlank())) {
                throw new IllegalArgumentException("拒绝Case必须声明expectedErrorType");
            }
            if (persist && expectedPlanVersion < 1) {
                throw new IllegalArgumentException("持久化Case必须声明新计划版本");
            }
            if (labelReason == null || labelReason.isBlank()) {
                throw new IllegalArgumentException("labelReason不能为空");
            }
        }
    }
}