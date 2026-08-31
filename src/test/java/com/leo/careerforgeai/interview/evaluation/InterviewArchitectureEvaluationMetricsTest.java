package com.leo.careerforgeai.interview.evaluation;

import com.leo.careerforgeai.interview.domain.EvidenceConsistencyVerdict;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证固定评测指标、公平对照约束及非法证据引用统计
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
class InterviewArchitectureEvaluationMetricsTest {

    @Test
    void shouldCompareQualityReliabilityAndCostWithoutPredeclaringWinner() {
        InterviewArchitectureEvaluationDataset dataset = InterviewArchitectureEvaluationDataset.load();
        var baseline = new InterviewArchitectureEvaluationMetrics.EvaluationRun(
                InterviewArchitectureEvaluationMetrics.Architecture.SINGLE_REVIEW_BASELINE,
                dataset.cases().stream().map(this::baselineObservation).toList()
        );
        var graph = new InterviewArchitectureEvaluationMetrics.EvaluationRun(
                InterviewArchitectureEvaluationMetrics.Architecture.MULTI_ROLE_GRAPH,
                dataset.cases().stream().map(this::perfectGraphObservation).toList()
        );

        var comparison = InterviewArchitectureEvaluationMetrics.compare(dataset, baseline, graph);

        assertThat(comparison.multiRoleGraph().successRatePercent()).isEqualTo(100.0);
        assertThat(comparison.multiRoleGraph().targetSkillCoveragePercent()).isEqualTo(100.0);
        assertThat(comparison.multiRoleGraph().scoreWithinGoldRangePercent()).isEqualTo(100.0);
        assertThat(comparison.multiRoleGraph().coveredConceptRecallPercent()).isEqualTo(100.0);
        assertThat(comparison.multiRoleGraph().issueConceptRecallPercent()).isEqualTo(100.0);
        assertThat(comparison.multiRoleGraph().evidenceVerdictAccuracyPercent()).isEqualTo(100.0);
        assertThat(comparison.multiRoleGraph().evidenceReferencePrecisionPercent()).isEqualTo(100.0);
        assertThat(comparison.multiRoleGraph().evidenceReferenceRecallPercent()).isEqualTo(100.0);
        assertThat(comparison.multiRoleGraph().legalEvidenceReferenceRatePercent()).isEqualTo(100.0);
        assertThat(comparison.multiRoleGraph().actionTopicRecallPercent()).isEqualTo(100.0);
        assertThat(comparison.baseline().legalEvidenceReferenceRatePercent()).isEqualTo(0.0);
        assertThat(comparison.baseline().totalModelCalls()).isEqualTo(3);
        assertThat(comparison.multiRoleGraph().totalModelCalls()).isEqualTo(9);
        assertThat(comparison.baseline().totalTokens()).isEqualTo(3_000);
        assertThat(comparison.multiRoleGraph().totalTokens()).isEqualTo(6_000);
        assertThat(comparison.multiRoleGraph().duplicateModelSideEffectCount()).isZero();
        assertThat(comparison.multiRoleGraph().stateRegressionCount()).isZero();
        assertThat(comparison.multiRoleGraph().crossOwnerLeakCount()).isZero();
    }

    @Test
    void shouldRejectRunsThatDoNotUseTheSameFixedCases() {
        InterviewArchitectureEvaluationDataset dataset = InterviewArchitectureEvaluationDataset.load();
        var incomplete = new InterviewArchitectureEvaluationMetrics.EvaluationRun(
                InterviewArchitectureEvaluationMetrics.Architecture.SINGLE_REVIEW_BASELINE,
                List.of(baselineObservation(dataset.cases().getFirst()))
        );

        assertThatThrownBy(() -> InterviewArchitectureEvaluationMetrics.evaluate(dataset, incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("完全相同的caseId");
    }

    private InterviewArchitectureEvaluationMetrics.CaseObservation perfectGraphObservation(
            InterviewArchitectureEvaluationDataset.EvaluationCase evaluationCase
    ) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        evaluationCase.expectedScoreRanges().forEach((dimension, range) -> scores.put(dimension, range.minimum()));
        return new InterviewArchitectureEvaluationMetrics.CaseObservation(
                evaluationCase.caseId(),
                true,
                Set.copyOf(evaluationCase.targetSkills()),
                1,
                0,
                scores,
                Set.copyOf(evaluationCase.requiredCoveredConcepts()),
                Set.copyOf(evaluationCase.requiredIssueConcepts()),
                evaluationCase.expectedEvidenceVerdict(),
                evaluationCase.expectedEvidenceReferenceIds(),
                Set.copyOf(evaluationCase.expectedActionTopics()),
                evaluationCase.expectedActionTopics().size(),
                evaluationCase.expectedActionTopics().size(),
                true,
                0,
                0,
                0,
                3,
                2_000,
                graphDuration(evaluationCase.caseId())
        );
    }

    private InterviewArchitectureEvaluationMetrics.CaseObservation baselineObservation(
            InterviewArchitectureEvaluationDataset.EvaluationCase evaluationCase
    ) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        evaluationCase.scoreDimensions().forEach(dimension -> scores.put(dimension, 5));
        List<String> references = evaluationCase.evidenceByChunkId().isEmpty()
                ? List.of() : List.of("f".repeat(64));
        return new InterviewArchitectureEvaluationMetrics.CaseObservation(
                evaluationCase.caseId(),
                true,
                Set.of(evaluationCase.targetSkills().getFirst()),
                1,
                0,
                scores,
                Set.of(evaluationCase.requiredCoveredConcepts().getFirst()),
                Set.of(evaluationCase.requiredIssueConcepts().getFirst()),
                EvidenceConsistencyVerdict.NOT_APPLICABLE,
                references,
                Set.of(evaluationCase.expectedActionTopics().getFirst()),
                1,
                1,
                true,
                0,
                0,
                0,
                1,
                1_000,
                baselineDuration(evaluationCase.caseId())
        );
    }

    private long baselineDuration(String caseId) {
        return switch (caseId) {
            case "TECHNICAL_KNOWLEDGE_001" -> 150;
            case "PROJECT_DEEP_DIVE_001" -> 200;
            case "SYSTEM_DESIGN_001" -> 250;
            default -> throw new IllegalArgumentException("未知caseId：" + caseId);
        };
    }

    private long graphDuration(String caseId) {
        return switch (caseId) {
            case "TECHNICAL_KNOWLEDGE_001" -> 250;
            case "PROJECT_DEEP_DIVE_001" -> 300;
            case "SYSTEM_DESIGN_001" -> 350;
            default -> throw new IllegalArgumentException("未知caseId：" + caseId);
        };
    }
}