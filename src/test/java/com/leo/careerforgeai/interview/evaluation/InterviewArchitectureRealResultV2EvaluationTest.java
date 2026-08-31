package com.leo.careerforgeai.interview.evaluation;

import com.leo.careerforgeai.interview.domain.EvidenceConsistencyVerdict;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * @program: CareerForge-AI
 * @description: 固化并验收扩展九Case真实架构对照结果，不预设多角色Graph必然更优
 * @author: Miao Zheng
 * @date: 2026-08-31
 */
class InterviewArchitectureRealResultV2EvaluationTest {

    @Test
    void shouldExposeQualityCostAndSafetyOutcomeOfExpandedEvaluation() {
        RealResultArtifact artifact = loadArtifact();
        InterviewArchitectureEvaluationDataset dataset = InterviewArchitectureEvaluationDataset.load(
                "interview/evaluation/interview-architecture-cases-v2.json"
        );
        InterviewArchitectureEvaluationMetrics.Comparison comparison = InterviewArchitectureEvaluationMetrics.compare(
                dataset,
                new InterviewArchitectureEvaluationMetrics.EvaluationRun(
                        InterviewArchitectureEvaluationMetrics.Architecture.SINGLE_REVIEW_BASELINE,
                        artifact.cases().stream().map(result -> baselineObservation(dataset, result)).toList()
                ),
                new InterviewArchitectureEvaluationMetrics.EvaluationRun(
                        InterviewArchitectureEvaluationMetrics.Architecture.MULTI_ROLE_GRAPH,
                        artifact.cases().stream().map(result -> graphObservation(dataset, result)).toList()
                )
        );
        InterviewArchitectureEvaluationMetrics.Metrics baseline = comparison.baseline();
        InterviewArchitectureEvaluationMetrics.Metrics graph = comparison.multiRoleGraph();

        assertThat(artifact.schemaVersion()).isEqualTo("interview-architecture-real-result-v2");
        assertThat(artifact.evaluationSetVersion()).isEqualTo(dataset.evaluationSetVersion());
        assertThat(artifact.model()).isEqualTo("deepseek-v4-flash");
        assertThat(artifact.reportPromptVersion()).isEqualTo("report-coach-v4");
        assertThat(artifact.cases()).hasSize(9);
        assertThat(artifact.limitations()).hasSize(6);

        assertThat(baseline.successRatePercent()).isEqualTo(100.0);
        assertThat(graph.successRatePercent()).isEqualTo(100.0);
        assertThat(baseline.scoreWithinGoldRangePercent()).isCloseTo(96.30, within(0.01));
        assertThat(graph.scoreWithinGoldRangePercent()).isCloseTo(85.19, within(0.01));
        assertThat(baseline.coveredConceptRecallPercent()).isCloseTo(82.14, within(0.01));
        assertThat(graph.coveredConceptRecallPercent()).isCloseTo(85.71, within(0.01));
        assertThat(baseline.issueConceptRecallPercent()).isEqualTo(96.0);
        assertThat(graph.issueConceptRecallPercent()).isEqualTo(96.0);
        assertThat(baseline.evidenceVerdictAccuracyPercent()).isCloseTo(88.89, within(0.01));
        assertThat(graph.evidenceVerdictAccuracyPercent()).isCloseTo(88.89, within(0.01));
        assertThat(baseline.evidenceReferencePrecisionPercent()).isCloseTo(83.33, within(0.01));
        assertThat(graph.evidenceReferencePrecisionPercent()).isCloseTo(83.33, within(0.01));
        assertThat(baseline.evidenceReferenceRecallPercent()).isEqualTo(100.0);
        assertThat(graph.evidenceReferenceRecallPercent()).isEqualTo(100.0);
        assertThat(baseline.legalEvidenceReferenceRatePercent()).isEqualTo(100.0);
        assertThat(graph.legalEvidenceReferenceRatePercent()).isEqualTo(100.0);
        assertThat(baseline.actionTopicRecallPercent()).isCloseTo(95.24, within(0.01));
        assertThat(graph.actionTopicRecallPercent()).isCloseTo(95.24, within(0.01));

        assertThat(baseline.totalModelCalls()).isEqualTo(9);
        assertThat(graph.totalModelCalls()).isEqualTo(27);
        assertThat(baseline.totalTokens()).isEqualTo(9_210);
        assertThat(graph.totalTokens()).isEqualTo(30_043);
        assertThat(baseline.p50DurationMs()).isEqualTo(3_498);
        assertThat(baseline.p95DurationMs()).isEqualTo(3_992);
        assertThat(graph.p50DurationMs()).isEqualTo(6_816);
        assertThat(graph.p95DurationMs()).isEqualTo(8_583);

        assertThat(graph.scoreWithinGoldRangePercent()).isLessThan(baseline.scoreWithinGoldRangePercent());
        assertThat(graph.coveredConceptRecallPercent()).isGreaterThan(baseline.coveredConceptRecallPercent());
        assertThat(graph.totalTokens() * 1.0 / baseline.totalTokens()).isCloseTo(3.262, within(0.001));

        Set<String> unsafeCandidateCases = Set.of(
                "TECHNICAL_KNOWLEDGE_001",
                "TECHNICAL_KNOWLEDGE_002",
                "PROJECT_DEEP_DIVE_001",
                "PROJECT_DEEP_DIVE_003",
                "SYSTEM_DESIGN_001",
                "SYSTEM_DESIGN_003"
        );
        assertThat(artifact.cases()).filteredOn(result -> unsafeCandidateCases.contains(result.caseId()))
                .allSatisfy(result -> {
                    assertThat(result.multiRoleGraph().reportStrengthCount()).isZero();
                    assertThat(result.multiRoleGraph().memoryCandidateCount()).isZero();
                });
        assertThat(caseResult(artifact, "TECHNICAL_KNOWLEDGE_003").multiRoleGraph().memoryCandidateCount()).isEqualTo(3);
        assertThat(caseResult(artifact, "PROJECT_DEEP_DIVE_002").multiRoleGraph().memoryCandidateCount()).isEqualTo(3);
        assertThat(caseResult(artifact, "SYSTEM_DESIGN_002").multiRoleGraph().memoryCandidateCount()).isEqualTo(3);

        Pattern sha256 = Pattern.compile("[0-9a-f]{64}");
        assertThat(artifact.cases()).allSatisfy(result -> {
            assertThat(result.baseline().responseHash()).matches(sha256);
            assertThat(result.multiRoleGraph().technicalResponseHash()).matches(sha256);
            assertThat(result.multiRoleGraph().evidenceResponseHash()).matches(sha256);
            assertThat(result.multiRoleGraph().reportResponseHash()).matches(sha256);
        });

        System.out.printf(
                Locale.ROOT,
                "evaluationSet=%s, baselineScore=%.2f%%, graphScore=%.2f%%, baselineCovered=%.2f%%, graphCovered=%.2f%%, baselineTokens=%d, graphTokens=%d, tokenRatio=%.3f, baselineP50Ms=%d, graphP50Ms=%d%n",
                artifact.evaluationSetVersion(),
                baseline.scoreWithinGoldRangePercent(),
                graph.scoreWithinGoldRangePercent(),
                baseline.coveredConceptRecallPercent(),
                graph.coveredConceptRecallPercent(),
                baseline.totalTokens(),
                graph.totalTokens(),
                graph.totalTokens() * 1.0 / baseline.totalTokens(),
                baseline.p50DurationMs(),
                graph.p50DurationMs()
        );
    }

    private InterviewArchitectureEvaluationMetrics.CaseObservation baselineObservation(
            InterviewArchitectureEvaluationDataset dataset,
            RealCaseResult result
    ) {
        InterviewArchitectureEvaluationDataset.EvaluationCase gold = goldCase(dataset, result.caseId());
        BaselineResult observation = result.baseline();
        return new InterviewArchitectureEvaluationMetrics.CaseObservation(
                result.caseId(),
                true,
                Set.copyOf(gold.targetSkills()),
                1,
                0,
                observation.dimensionScores(),
                observation.adjudicatedCoveredConcepts(),
                observation.adjudicatedIssueConcepts(),
                observation.evidenceVerdict(),
                observation.evidenceReferenceIds(),
                observation.adjudicatedActionTopics(),
                0,
                observation.proposedActionCount(),
                false,
                0,
                0,
                0,
                1,
                observation.totalTokens(),
                observation.durationMs()
        );
    }

    private InterviewArchitectureEvaluationMetrics.CaseObservation graphObservation(
            InterviewArchitectureEvaluationDataset dataset,
            RealCaseResult result
    ) {
        InterviewArchitectureEvaluationDataset.EvaluationCase gold = goldCase(dataset, result.caseId());
        GraphResult observation = result.multiRoleGraph();
        return new InterviewArchitectureEvaluationMetrics.CaseObservation(
                result.caseId(),
                true,
                Set.copyOf(gold.targetSkills()),
                1,
                0,
                observation.dimensionScores(),
                observation.adjudicatedCoveredConcepts(),
                observation.adjudicatedIssueConcepts(),
                observation.evidenceVerdict(),
                observation.evidenceReferenceIds(),
                observation.adjudicatedActionTopics(),
                0,
                observation.proposedActionCount(),
                true,
                0,
                0,
                0,
                3,
                observation.totalTokens(),
                observation.durationMs()
        );
    }

    private InterviewArchitectureEvaluationDataset.EvaluationCase goldCase(
            InterviewArchitectureEvaluationDataset dataset,
            String caseId
    ) {
        return dataset.cases().stream().filter(evaluationCase -> evaluationCase.caseId().equals(caseId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("未知caseId：" + caseId));
    }

    private RealCaseResult caseResult(RealResultArtifact artifact, String caseId) {
        return artifact.cases().stream().filter(result -> result.caseId().equals(caseId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("未知caseId：" + caseId));
    }

    private RealResultArtifact loadArtifact() {
        String resourcePath = "interview/evaluation/interview-architecture-real-results-v2.json";
        InputStream resource = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (resource == null) throw new IllegalStateException("真实评测结果不存在：" + resourcePath);
        try (InputStream input = resource) {
            return JsonMapper.builder().build().readerFor(RealResultArtifact.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(input);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("真实评测结果解析或校验失败", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("真实评测结果关闭失败", exception);
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存扩展固定评测的元数据和逐Case真实结果
     * @author: Miao Zheng
     * @date: 2026-08-31
     * @param schemaVersion 结果结构版本
     * @param evaluationSetVersion 固定评测集版本
     * @param model 实际模型
     * @param baselinePromptVersion 单评审Prompt版本
     * @param technicalPromptVersion 技术评审Prompt版本
     * @param evidencePromptVersion 证据评审Prompt版本
     * @param reportPromptVersion 报告Prompt版本
     * @param limitations 本次评测的限制
     * @param cases 逐Case真实结果
     */
    private record RealResultArtifact(
            String schemaVersion,
            String evaluationSetVersion,
            String model,
            String baselinePromptVersion,
            String technicalPromptVersion,
            String evidencePromptVersion,
            String reportPromptVersion,
            List<String> limitations,
            List<RealCaseResult> cases
    ) {
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存同一固定Case在基线和多角色Graph下的结果
     * @author: Miao Zheng
     * @date: 2026-08-31
     * @param caseId 固定Case标识
     * @param baseline 单评审结果
     * @param multiRoleGraph 多角色Graph结果
     */
    private record RealCaseResult(
            String caseId,
            BaselineResult baseline,
            GraphResult multiRoleGraph
    ) {
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存单评审基线的人工裁决、成本和响应摘要
     * @author: Miao Zheng
     * @date: 2026-08-31
     * @param dimensionScores 技术评分
     * @param adjudicatedCoveredConcepts 人工确认的覆盖点
     * @param adjudicatedIssueConcepts 人工确认的问题点
     * @param evidenceVerdict 证据结论
     * @param evidenceReferenceIds 证据引用
     * @param adjudicatedActionTopics 人工确认的建议主题
     * @param proposedActionCount 建议总数
     * @param totalTokens Token总数
     * @param durationMs 耗时
     * @param responseHash 响应哈希
     */
    private record BaselineResult(
            Map<String, Integer> dimensionScores,
            Set<String> adjudicatedCoveredConcepts,
            Set<String> adjudicatedIssueConcepts,
            EvidenceConsistencyVerdict evidenceVerdict,
            List<String> evidenceReferenceIds,
            Set<String> adjudicatedActionTopics,
            int proposedActionCount,
            long totalTokens,
            long durationMs,
            String responseHash
    ) {
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存多角色Graph的人工裁决、报告安全结果、成本和响应摘要
     * @author: Miao Zheng
     * @date: 2026-08-31
     * @param dimensionScores 技术评分
     * @param adjudicatedCoveredConcepts 人工确认的覆盖点
     * @param adjudicatedIssueConcepts 人工确认的问题点
     * @param evidenceVerdict 证据结论
     * @param evidenceReferenceIds 证据引用
     * @param adjudicatedActionTopics 人工确认的建议主题
     * @param proposedActionCount 建议总数
     * @param reportStrengthCount 报告优势数量
     * @param memoryCandidateCount Memory候选数量
     * @param totalTokens Token总数
     * @param durationMs 耗时
     * @param technicalResponseHash 技术评审响应哈希
     * @param evidenceResponseHash 证据评审响应哈希
     * @param reportResponseHash 报告响应哈希
     */
    private record GraphResult(
            Map<String, Integer> dimensionScores,
            Set<String> adjudicatedCoveredConcepts,
            Set<String> adjudicatedIssueConcepts,
            EvidenceConsistencyVerdict evidenceVerdict,
            List<String> evidenceReferenceIds,
            Set<String> adjudicatedActionTopics,
            int proposedActionCount,
            int reportStrengthCount,
            int memoryCandidateCount,
            long totalTokens,
            long durationMs,
            String technicalResponseHash,
            String evidenceResponseHash,
            String reportResponseHash
    ) {
    }
}