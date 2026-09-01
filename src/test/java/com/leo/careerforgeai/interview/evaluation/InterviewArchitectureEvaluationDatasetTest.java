package com.leo.careerforgeai.interview.evaluation;

import com.leo.careerforgeai.interview.domain.review.EvidenceConsistencyVerdict;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestionType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证阶段六固定评测集能够严格加载并覆盖题型、证据结论和Gold边界
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
class InterviewArchitectureEvaluationDatasetTest {

    @Test
    void shouldKeepOriginalThreeCaseDatasetStable() {
        InterviewArchitectureEvaluationDataset dataset = InterviewArchitectureEvaluationDataset.load();

        assertThat(dataset.evaluationSetVersion()).isEqualTo("careerforge-interview-architecture-eval-v1");
        assertThat(dataset.cases()).hasSize(3);
        assertThat(dataset.cases()).extracting(InterviewArchitectureEvaluationDataset.EvaluationCase::questionType)
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(InterviewQuestionType.class));
        assertThat(dataset.cases()).extracting(InterviewArchitectureEvaluationDataset.EvaluationCase::expectedEvidenceVerdict)
                .containsExactlyInAnyOrder(
                        EvidenceConsistencyVerdict.NOT_APPLICABLE,
                        EvidenceConsistencyVerdict.PARTIALLY_SUPPORTED,
                        EvidenceConsistencyVerdict.CONTRADICTED
                );
        assertGoldLabels(dataset);
    }

    @Test
    void shouldLoadPreRegisteredNineCaseDatasetWithCompleteCoverage() {
        InterviewArchitectureEvaluationDataset dataset = InterviewArchitectureEvaluationDataset.load(
                "interview/evaluation/interview-architecture-cases-v2.json"
        );

        assertThat(dataset.evaluationSetVersion()).isEqualTo("careerforge-interview-architecture-eval-v2");
        assertThat(dataset.cases()).hasSize(9);
        for (InterviewQuestionType questionType : InterviewQuestionType.values()) {
            assertThat(dataset.cases()).filteredOn(evaluationCase -> evaluationCase.questionType() == questionType)
                    .as("%s必须固定包含3条Case", questionType)
                    .hasSize(3);
        }
        assertThat(dataset.cases()).extracting(InterviewArchitectureEvaluationDataset.EvaluationCase::expectedEvidenceVerdict)
                .containsAll(EnumSet.allOf(EvidenceConsistencyVerdict.class));
        assertGoldLabels(dataset);
    }

    private void assertGoldLabels(InterviewArchitectureEvaluationDataset dataset) {
        assertThat(dataset.cases()).allSatisfy(evaluationCase -> {
            assertThat(evaluationCase.expectedScoreRanges().keySet())
                    .isEqualTo(Set.copyOf(evaluationCase.scoreDimensions()));
            assertThat(evaluationCase.targetSkills()).isNotEmpty();
            assertThat(evaluationCase.requiredCoveredConcepts()).isNotEmpty();
            assertThat(evaluationCase.requiredIssueConcepts()).isNotEmpty();
            assertThat(evaluationCase.expectedActionTopics()).isNotEmpty();
        });
    }
}