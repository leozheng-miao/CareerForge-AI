package com.leo.careerforgeai.career.evaluation;

import com.leo.careerforgeai.career.application.skillgap.DeterministicSkillGapMatcher;
import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.memory.application.profile.ConfirmedSkillProfile;
import com.leo.careerforgeai.memory.domain.profile.*;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 使用固定数据集评测确定性SkillGap状态和Memory证据引用边界
 * @author: Miao Zheng
 * @date: 2026-08-18
 */
class SkillGapFixedEvaluationTest {

    private static final String DATASET_RESOURCE =
            "training/evaluation/skill-gap-cases.json";
    private static final ActorId ACTOR = new ActorId("skill-gap-evaluation-actor");
    private static final Instant NOW = Instant.parse("2026-08-18T06:00:00Z");

    @Test
    void shouldEvaluateFixedSkillGapCasesAndReportMetrics() throws Exception {
        EvaluationDataset dataset = loadDataset();
        DeterministicSkillGapMatcher matcher = new DeterministicSkillGapMatcher();
        int taskSuccess = 0;
        int matched = 0;
        int unverified = 0;
        int missing = 0;
        int partial = 0;
        int legalEvidenceRefs = 0;
        int totalEvidenceRefs = 0;

        for (EvaluationCase evaluationCase : dataset.cases()) {
            List<MemoryItem> evidence = evidence(evaluationCase);
            ConfirmedSkillProfile profile = new ConfirmedSkillProfile(
                    ACTOR,
                    evaluationCase.profileVersion(),
                    evidence
            );
            List<SkillGapSnapshot.GapItem> gaps = matcher.match(
                    targetRole(evaluationCase),
                    profile
            );

            long actualMatched = count(gaps, SkillGapSnapshot.GapStatus.MATCHED);
            long actualUnverified = count(gaps, SkillGapSnapshot.GapStatus.UNVERIFIED);
            long actualMissing = count(gaps, SkillGapSnapshot.GapStatus.MISSING);
            long actualPartial = count(gaps, SkillGapSnapshot.GapStatus.PARTIAL);
            boolean caseSuccess = actualMatched == evaluationCase.expectedMatched()
                    && actualUnverified == evaluationCase.expectedUnverified()
                    && actualMissing == evaluationCase.expectedMissing()
                    && actualPartial == evaluationCase.expectedPartial();

            assertThat(caseSuccess)
                    .as("%s：%s", evaluationCase.caseId(), evaluationCase.labelReason())
                    .isTrue();
            taskSuccess++;
            matched += (int) actualMatched;
            unverified += (int) actualUnverified;
            missing += (int) actualMissing;
            partial += (int) actualPartial;

            Set<UUID> allowedEvidenceIds = new HashSet<>();
            evidence.forEach(memory -> allowedEvidenceIds.add(memory.memoryId()));
            for (SkillGapSnapshot.GapItem gap : gaps) {
                for (UUID evidenceId : gap.evidenceMemoryIds()) {
                    totalEvidenceRefs++;
                    if (allowedEvidenceIds.contains(evidenceId)) legalEvidenceRefs++;
                }
            }
        }

        assertThat(taskSuccess).isEqualTo(5);
        assertThat(matched).isEqualTo(2);
        assertThat(unverified).isEqualTo(2);
        assertThat(missing).isEqualTo(4);
        assertThat(partial).isZero();
        assertThat(legalEvidenceRefs).isEqualTo(totalEvidenceRefs).isEqualTo(4);

        System.out.printf(
                Locale.ROOT,
                """
                ================= SkillGap Fixed Evaluation =================
                Task Success Rate: %d/%d
                Gap Evidence Reference Legal Rate: %d/%d
                MATCHED: %d
                UNVERIFIED: %d
                MISSING: %d
                PARTIAL: %d
                PARTIAL Semantic Quality: N/A (deterministic-v1 does not infer it)
                Model Token/Latency: N/A (no model call)
                =============================================================
                """,
                taskSuccess,
                dataset.cases().size(),
                legalEvidenceRefs,
                totalEvidenceRefs,
                matched,
                unverified,
                missing,
                partial
        );
    }

    private static long count(
            List<SkillGapSnapshot.GapItem> gaps,
            SkillGapSnapshot.GapStatus status
    ) {
        return gaps.stream().filter(gap -> gap.status() == status).count();
    }

    private static TargetRole targetRole(EvaluationCase evaluationCase) {
        JobRequirements requirements = new JobRequirements(
                "固定Java后端岗位",
                evaluationCase.programmingLanguages(),
                evaluationCase.backendRequirements(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        return TargetRole.createConfirmed(
                id(evaluationCase.caseId() + "-target-role"),
                ACTOR,
                1,
                "fixed-job-description",
                "a".repeat(64),
                "job-requirements-parser-v1",
                "job-requirements-prompt-v1",
                requirements,
                NOW
        );
    }

    private static List<MemoryItem> evidence(EvaluationCase evaluationCase) {
        List<MemoryItem> result = new ArrayList<>();
        for (int index = 0; index < evaluationCase.evidence().size(); index++) {
            EvidenceInput input = evaluationCase.evidence().get(index);
            String seed = evaluationCase.caseId() + "-evidence-" + index;
            MemorySourceType sourceType = MemorySourceType.valueOf(input.sourceType());
            String sourceId = id(seed + "-source").toString();
            MemoryItem pending = MemoryItem.createPending(
                    id(seed + "-memory"),
                    ACTOR,
                    MemoryType.SKILL_EVIDENCE,
                    MemoryNormalizedKey.skillEvidence(input.skill()),
                    "固定评测技能证据：" + input.skill(),
                    new MemorySource(sourceType, sourceId, "b".repeat(64)),
                    List.of(sourceId),
                    NOW
            );
            MemoryDecision decision = MemoryDecision.create(
                    id(seed + "-decision"),
                    pending,
                    ACTOR,
                    MemoryDecisionType.CONFIRM,
                    null,
                    "固定SkillGap评测",
                    NOW.plusSeconds(index + 1L)
            );
            result.add(pending.applyDecision(decision));
        }
        return List.copyOf(result);
    }

    private static EvaluationDataset loadDataset() throws Exception {
        InputStream resource = SkillGapFixedEvaluationTest.class.getClassLoader()
                .getResourceAsStream(DATASET_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException("固定SkillGap评测集不存在：" + DATASET_RESOURCE);
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
     * @description: 定义SkillGap固定评测集
     * @author: Miao Zheng
     * @date: 2026-08-18
     * @param schemaVersion 数据结构版本
     * @param evaluationSetVersion 固定评测集版本
     * @param cases 固定评测Case
     */
    private record EvaluationDataset(
            String schemaVersion,
            String evaluationSetVersion,
            List<EvaluationCase> cases
    ) {
        private EvaluationDataset {
            if (!"skill-gap-evaluation-v1".equals(schemaVersion)) {
                throw new IllegalArgumentException("schemaVersion不受支持");
            }
            if (!"careerforge-skill-gap-eval-v1".equals(evaluationSetVersion)) {
                throw new IllegalArgumentException("evaluationSetVersion不受支持");
            }
            if (cases == null || cases.size() != 5 || cases.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("SkillGap固定评测集必须包含5条Case");
            }
            cases = List.copyOf(cases);
            if (cases.stream().map(EvaluationCase::caseId).distinct().count() != cases.size()) {
                throw new IllegalArgumentException("caseId不能重复");
            }
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义单条SkillGap固定输入与预期状态数量
     * @author: Miao Zheng
     * @date: 2026-08-18
     * @param caseId Case唯一ID
     * @param scenario 场景类型
     * @param profileVersion 固定技能画像版本
     * @param programmingLanguages 岗位编程语言要求
     * @param backendRequirements 岗位后端与基础设施要求
     * @param evidence 已确认技能证据
     * @param expectedMatched 预期MATCHED数量
     * @param expectedUnverified 预期UNVERIFIED数量
     * @param expectedMissing 预期MISSING数量
     * @param expectedPartial 预期PARTIAL数量
     * @param labelReason 标注依据
     */
    private record EvaluationCase(
            String caseId,
            String scenario,
            long profileVersion,
            List<String> programmingLanguages,
            List<String> backendRequirements,
            List<EvidenceInput> evidence,
            int expectedMatched,
            int expectedUnverified,
            int expectedMissing,
            int expectedPartial,
            String labelReason
    ) {
        private EvaluationCase {
            if (caseId == null || !caseId.matches("skill-gap-eval-[0-9]{3}")) {
                throw new IllegalArgumentException("caseId格式不合法");
            }
            if (scenario == null || scenario.isBlank() || profileVersion < 0) {
                throw new IllegalArgumentException("场景或画像版本不合法");
            }
            programmingLanguages = immutable(programmingLanguages, "programmingLanguages");
            backendRequirements = immutable(backendRequirements, "backendRequirements");
            if (programmingLanguages.isEmpty() && backendRequirements.isEmpty()) {
                throw new IllegalArgumentException("岗位必须包含可评估技能要求");
            }
            if (evidence == null || evidence.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("evidence不能为空或包含null");
            }
            evidence = List.copyOf(evidence);
            if (expectedMatched < 0
                    || expectedUnverified < 0
                    || expectedMissing < 0
                    || expectedPartial < 0) {
                throw new IllegalArgumentException("预期状态数量不能小于0");
            }
            int requirementCount = programmingLanguages.size() + backendRequirements.size();
            int expectedCount = expectedMatched
                    + expectedUnverified
                    + expectedMissing
                    + expectedPartial;
            if (requirementCount != expectedCount) {
                throw new IllegalArgumentException("岗位要求数量与预期Gap数量不一致");
            }
            if (labelReason == null || labelReason.isBlank()) {
                throw new IllegalArgumentException("labelReason不能为空");
            }
        }

        private static List<String> immutable(List<String> values, String fieldName) {
            if (values == null || values.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException(fieldName + "不能为空或包含空值");
            }
            return List.copyOf(values);
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义固定评测中的技能证据输入
     * @author: Miao Zheng
     * @date: 2026-08-18
     * @param skill 技能标准名称
     * @param sourceType Memory来源类型
     */
    private record EvidenceInput(
            String skill,
            String sourceType
    ) {
        private EvidenceInput {
            if (skill == null || skill.isBlank()) {
                throw new IllegalArgumentException("skill不能为空");
            }
            try {
                MemorySourceType.valueOf(sourceType);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("sourceType不受支持", exception);
            }
        }
    }
}