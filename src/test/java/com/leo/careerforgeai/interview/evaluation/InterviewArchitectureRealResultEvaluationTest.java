package com.leo.careerforgeai.interview.evaluation;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * @program: CareerForge-AI
 * @description: 固化并验收阶段六单评审基线与多角色Graph首次真实固定评测结果
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
class InterviewArchitectureRealResultEvaluationTest {

    private static final String RESULT_RESOURCE = "interview/evaluation/interview-architecture-real-results-v1.json";
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    @Test
    void shouldPreserveRealComparisonWithoutClaimingUnprovenMultiRoleGain() {
        RealResultArtifact artifact = loadArtifact();
        InterviewArchitectureEvaluationDataset dataset = InterviewArchitectureEvaluationDataset.load();
        InterviewArchitectureEvaluationMetrics.Comparison comparison = InterviewArchitectureEvaluationMetrics.compare(
                dataset, artifact.baseline(), artifact.multiRoleGraph()
        );
        InterviewArchitectureEvaluationMetrics.Metrics baseline = comparison.baseline();
        InterviewArchitectureEvaluationMetrics.Metrics graph = comparison.multiRoleGraph();

        assertThat(artifact.schemaVersion()).isEqualTo("interview-architecture-real-result-v1");
        assertThat(artifact.evaluationSetVersion()).isEqualTo(dataset.evaluationSetVersion());
        assertThat(artifact.model()).isEqualTo("deepseek-v4-flash");
        assertThat(artifact.reportPromptVersion()).isEqualTo("report-coach-v4");
        assertThat(artifact.limitations()).hasSize(4);

        assertThat(baseline.failedCount()).isZero();
        assertThat(graph.failedCount()).isZero();
        assertThat(baseline.successRatePercent()).isEqualTo(100.0);
        assertThat(graph.successRatePercent()).isEqualTo(100.0);
        assertThat(graph.targetSkillCoveragePercent()).isEqualTo(baseline.targetSkillCoveragePercent());
        assertThat(graph.coveredConceptRecallPercent()).isEqualTo(baseline.coveredConceptRecallPercent());
        assertThat(graph.issueConceptRecallPercent()).isEqualTo(baseline.issueConceptRecallPercent());
        assertThat(graph.evidenceVerdictAccuracyPercent()).isEqualTo(baseline.evidenceVerdictAccuracyPercent());
        assertThat(graph.evidenceReferencePrecisionPercent()).isEqualTo(baseline.evidenceReferencePrecisionPercent());
        assertThat(graph.evidenceReferenceRecallPercent()).isEqualTo(baseline.evidenceReferenceRecallPercent());
        assertThat(graph.legalEvidenceReferenceRatePercent()).isEqualTo(baseline.legalEvidenceReferenceRatePercent());
        assertThat(graph.actionTopicRecallPercent()).isEqualTo(baseline.actionTopicRecallPercent());

        assertThat(baseline.scoreWithinGoldRangePercent()).isCloseTo(77.78, within(0.01));
        assertThat(graph.scoreWithinGoldRangePercent()).isCloseTo(66.67, within(0.01));
        assertThat(graph.scoreWithinGoldRangePercent()).isLessThan(baseline.scoreWithinGoldRangePercent());

        assertThat(baseline.totalModelCalls()).isEqualTo(3);
        assertThat(graph.totalModelCalls()).isEqualTo(9);
        assertThat(baseline.totalTokens()).isEqualTo(2_992);
        assertThat(graph.totalTokens()).isEqualTo(8_979);
        assertThat(baseline.p50DurationMs()).isEqualTo(2_567);
        assertThat(baseline.p95DurationMs()).isEqualTo(3_468);
        assertThat(graph.p50DurationMs()).isEqualTo(5_021);
        assertThat(graph.p95DurationMs()).isEqualTo(5_252);

        double tokenRatio = graph.totalTokens() * 1.0 / baseline.totalTokens();
        assertThat(tokenRatio).isCloseTo(3.001, within(0.001));
        assertThat(artifact.responseDigests()).hasSize(3);
        assertThat(artifact.responseDigests().stream().map(ResponseDigest::caseId).collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        "TECHNICAL_KNOWLEDGE_001",
                        "PROJECT_DEEP_DIVE_001",
                        "SYSTEM_DESIGN_001"
                ));
        assertThat(artifact.responseDigests()).allSatisfy(digest -> {
            assertThat(digest.baselineResponseHash()).matches(SHA256_PATTERN);
            assertThat(digest.technicalResponseHash()).matches(SHA256_PATTERN);
            assertThat(digest.evidenceResponseHash()).matches(SHA256_PATTERN);
            assertThat(digest.reportResponseHash()).matches(SHA256_PATTERN);
            assertThat(digest.reportStrengthCount()).isZero();
            assertThat(digest.memoryCandidateCount()).isZero();
        });

        System.out.printf(
                Locale.ROOT,
                "evaluationSet=%s, baselineScoreRange=%.2f%%, graphScoreRange=%.2f%%, baselineTokens=%d, graphTokens=%d, tokenRatio=%.3f, baselineP50Ms=%d, graphP50Ms=%d%n",
                artifact.evaluationSetVersion(),
                baseline.scoreWithinGoldRangePercent(),
                graph.scoreWithinGoldRangePercent(),
                baseline.totalTokens(),
                graph.totalTokens(),
                tokenRatio,
                baseline.p50DurationMs(),
                graph.p50DurationMs()
        );
    }

    private RealResultArtifact loadArtifact() {
        InputStream resource = getClass().getClassLoader().getResourceAsStream(RESULT_RESOURCE);
        if (resource == null) throw new IllegalStateException("真实评测结果不存在：" + RESULT_RESOURCE);
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
     * @description: 保存一次已冻结的真实架构对照结果及其适用边界
     * @author: Miao Zheng
     * @date: 2026-08-30
     * @param schemaVersion 结果结构版本
     * @param evaluationSetVersion 固定评测集版本
     * @param model 实际模型
     * @param baselinePromptVersion 单评审基线Prompt版本
     * @param technicalPromptVersion 技术评审Prompt版本
     * @param evidencePromptVersion 证据评审Prompt版本
     * @param reportPromptVersion 报告Prompt版本
     * @param measurementScope 本次结果的测量范围
     * @param limitations 不能由本次结果推出的结论
     * @param baseline 单评审基线观察
     * @param multiRoleGraph 多角色Graph观察
     * @param responseDigests 原始模型响应摘要
     */
    private record RealResultArtifact(
            String schemaVersion,
            String evaluationSetVersion,
            String model,
            String baselinePromptVersion,
            String technicalPromptVersion,
            String evidencePromptVersion,
            String reportPromptVersion,
            String measurementScope,
            List<String> limitations,
            InterviewArchitectureEvaluationMetrics.EvaluationRun baseline,
            InterviewArchitectureEvaluationMetrics.EvaluationRun multiRoleGraph,
            List<ResponseDigest> responseDigests
    ) {
    }

    /**
     * @program: CareerForge-AI
     * @description: 绑定单个固定Case的模型响应哈希和报告安全裁决结果
     * @author: Miao Zheng
     * @date: 2026-08-30
     * @param caseId 固定Case标识
     * @param baselineResponseHash 单评审响应哈希
     * @param technicalResponseHash 技术评审响应哈希
     * @param evidenceResponseHash 证据评审响应哈希
     * @param reportResponseHash 报告响应哈希
     * @param reportStrengthCount 最终报告优势数量
     * @param memoryCandidateCount 最终Memory候选数量
     */
    private record ResponseDigest(
            String caseId,
            String baselineResponseHash,
            String technicalResponseHash,
            String evidenceResponseHash,
            String reportResponseHash,
            int reportStrengthCount,
            int memoryCandidateCount
    ) {
    }
}