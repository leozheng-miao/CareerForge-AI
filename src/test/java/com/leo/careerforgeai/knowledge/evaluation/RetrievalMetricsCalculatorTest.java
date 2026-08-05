package com.leo.careerforgeai.knowledge.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalMetricsCalculatorTest {

    private final RetrievalMetricsCalculator calculator = new RetrievalMetricsCalculator();

    @Test
    void shouldCalculatePerfectRecallAndFirstRankHit() {
        RetrievalCaseMetrics metrics = calculator.calculate(
                "rag-eval-001",
                Set.of("chunk-a", "chunk-c"),
                List.of("chunk-a", "chunk-b", "chunk-c", "chunk-d"),
                3
        );

        assertThat(metrics.relevantCount()).isEqualTo(2);
        assertThat(metrics.retrievedCount()).isEqualTo(3);
        assertThat(metrics.hitCount()).isEqualTo(2);
        assertThat(metrics.recallAtK()).isEqualTo(1.0);
        assertThat(metrics.precisionAtK()).isCloseTo(2.0 / 3.0, within(1.0e-12));
        assertThat(metrics.reciprocalRank()).isEqualTo(1.0);
        assertThat(metrics.firstRelevantRank()).isEqualTo(1);
    }

    @Test
    void shouldCalculatePartialRecallAndReciprocalRank() {
        RetrievalCaseMetrics metrics = calculator.calculate(
                "rag-eval-004",
                Set.of("chunk-c", "chunk-d"),
                List.of("chunk-a", "chunk-b", "chunk-c", "chunk-d"),
                3
        );

        assertThat(metrics.hitCount()).isEqualTo(1);
        assertThat(metrics.recallAtK()).isEqualTo(0.5);
        assertThat(metrics.precisionAtK()).isCloseTo(1.0 / 3.0, within(1.0e-12));
        assertThat(metrics.reciprocalRank()).isCloseTo(1.0 / 3.0, within(1.0e-12));
        assertThat(metrics.firstRelevantRank()).isEqualTo(3);
    }

    @Test
    void shouldReturnZeroWhenTopKContainsNoRelevantChunk() {
        RetrievalCaseMetrics metrics = calculator.calculate(
                "rag-eval-003",
                Set.of("chunk-d"),
                List.of("chunk-a", "chunk-b", "chunk-c", "chunk-d"),
                3
        );

        assertThat(metrics.hitCount()).isZero();
        assertThat(metrics.recallAtK()).isZero();
        assertThat(metrics.precisionAtK()).isZero();
        assertThat(metrics.reciprocalRank()).isZero();
        assertThat(metrics.firstRelevantRank()).isZero();
    }

    @Test
    void shouldRejectInvalidMetricInput() {
        assertThatThrownBy(() -> calculator.calculate("rag-eval-001", Set.of(), List.of("chunk-a"), 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relevantChunkIds");

        assertThatThrownBy(() -> calculator.calculate("rag-eval-001", Set.of("chunk-a"), List.of("chunk-a", "chunk-a"), 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");

        assertThatThrownBy(() -> calculator.calculate("rag-eval-001", Set.of("chunk-a"), List.of("chunk-a"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK");
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}