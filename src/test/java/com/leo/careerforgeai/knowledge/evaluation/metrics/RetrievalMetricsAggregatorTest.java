package com.leo.careerforgeai.knowledge.evaluation.metrics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalMetricsAggregatorTest {

    private final RetrievalMetricsAggregator aggregator = new RetrievalMetricsAggregator();

    @Test
    void shouldAggregateQualityAndNearestRankLatencyPercentiles() {
        List<RetrievalCaseMeasurement> measurements = LongStream.rangeClosed(1, 20)
                .mapToObj(index -> new RetrievalCaseMeasurement(
                        new RetrievalCaseMetrics(
                                "rag-eval-%03d".formatted(index),
                                5,
                                1,
                                5,
                                1,
                                1.0,
                                0.2,
                                1.0,
                                1
                        ),
                        index
                ))
                .toList();

        RetrievalEvaluationSummary summary = aggregator.aggregate(RetrievalStrategy.BM25, measurements);

        assertThat(summary.strategy()).isEqualTo(RetrievalStrategy.BM25);
        assertThat(summary.topK()).isEqualTo(5);
        assertThat(summary.evaluatedCases()).isEqualTo(20);
        assertThat(summary.meanRecallAtK()).isEqualTo(1.0);
        assertThat(summary.meanPrecisionAtK()).isEqualTo(0.2);
        assertThat(summary.mrr()).isEqualTo(1.0);
        assertThat(summary.retrievalLatencyP50Ms()).isEqualTo(10);
        assertThat(summary.retrievalLatencyP95Ms()).isEqualTo(19);
    }

    @Test
    void shouldAverageDifferentCaseResults() {
        List<RetrievalCaseMeasurement> measurements = List.of(
                measurement("rag-eval-001", 1.0, 0.4, 1.0, 10),
                measurement("rag-eval-002", 0.5, 0.2, 0.5, 30)
        );

        RetrievalEvaluationSummary summary = aggregator.aggregate(RetrievalStrategy.VECTOR, measurements);

        assertThat(summary.meanRecallAtK()).isEqualTo(0.75);
        assertThat(summary.meanPrecisionAtK()).isCloseTo(0.3, org.assertj.core.data.Offset.offset(1.0e-12));
        assertThat(summary.mrr()).isEqualTo(0.75);
        assertThat(summary.retrievalLatencyP50Ms()).isEqualTo(10);
        assertThat(summary.retrievalLatencyP95Ms()).isEqualTo(30);
    }

    @Test
    void shouldRejectMixedTopKAndDuplicateCases() {
        RetrievalCaseMeasurement topFive = measurement("rag-eval-001", 1.0, 0.2, 1.0, 10);
        RetrievalCaseMeasurement topTen = new RetrievalCaseMeasurement(
                new RetrievalCaseMetrics("rag-eval-002", 10, 1, 10, 1, 1.0, 0.1, 1.0, 1),
                20
        );

        assertThatThrownBy(() -> aggregator.aggregate(RetrievalStrategy.BM25, List.of(topFive, topTen)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK");

        assertThatThrownBy(() -> aggregator.aggregate(RetrievalStrategy.BM25, List.of(topFive, topFive)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caseId");
    }

    private RetrievalCaseMeasurement measurement(String caseId, double recall, double precision, double reciprocalRank, long durationMs) {
        return new RetrievalCaseMeasurement(
                new RetrievalCaseMetrics(caseId, 5, 2, 5, 1, recall, precision, reciprocalRank, reciprocalRank == 0.0 ? 0 : (int) Math.round(1.0 / reciprocalRank)),
                durationMs
        );
    }
}