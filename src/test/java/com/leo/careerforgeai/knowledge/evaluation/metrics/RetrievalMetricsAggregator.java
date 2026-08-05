package com.leo.careerforgeai.knowledge.evaluation.metrics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RetrievalMetricsAggregator {

    /** 汇总同一种策略、同一个 Top K 下的全部可回答 Case。 */
    public RetrievalEvaluationSummary aggregate(RetrievalStrategy strategy, List<RetrievalCaseMeasurement> measurements) {
        if (strategy == null) throw new IllegalArgumentException("strategy 不能为空");
        if (measurements == null || measurements.isEmpty()) throw new IllegalArgumentException("measurements 不能为空");

        int topK = measurements.getFirst().metrics().topK();
        Set<String> caseIds = new HashSet<>();

        for (RetrievalCaseMeasurement measurement : measurements) {
            if (measurement == null) throw new IllegalArgumentException("measurements 不能包含空元素");
            if (measurement.metrics().topK() != topK) throw new IllegalArgumentException("同一次汇总中的 topK 必须一致");
            if (!caseIds.add(measurement.metrics().caseId())) throw new IllegalArgumentException("同一次汇总中 caseId 不能重复");
        }

        double meanRecallAtK = measurements.stream().mapToDouble(measurement -> measurement.metrics().recallAtK()).average().orElseThrow();
        double meanPrecisionAtK = measurements.stream().mapToDouble(measurement -> measurement.metrics().precisionAtK()).average().orElseThrow();
        double mrr = measurements.stream().mapToDouble(measurement -> measurement.metrics().reciprocalRank()).average().orElseThrow();
        List<Long> sortedDurations = measurements.stream().map(RetrievalCaseMeasurement::retrievalDurationMs).sorted().toList();

        return new RetrievalEvaluationSummary(
                strategy,
                topK,
                measurements.size(),
                meanRecallAtK,
                meanPrecisionAtK,
                mrr,
                nearestRankPercentile(sortedDurations, 0.50),
                nearestRankPercentile(sortedDurations, 0.95)
        );
    }

    /** 使用 nearest-rank 方法计算延迟百分位。 */
    private long nearestRankPercentile(List<Long> sortedValues, double percentile) {
        int rank = (int) Math.ceil(percentile * sortedValues.size());
        return sortedValues.get(Math.max(rank, 1) - 1);
    }
}