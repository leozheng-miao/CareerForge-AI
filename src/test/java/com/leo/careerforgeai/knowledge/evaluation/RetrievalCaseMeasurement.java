package com.leo.careerforgeai.knowledge.evaluation;

public record RetrievalCaseMeasurement(
        RetrievalCaseMetrics metrics,
        long retrievalDurationMs
) {

    public RetrievalCaseMeasurement {
        if (metrics == null) throw new IllegalArgumentException("metrics 不能为空");
        if (retrievalDurationMs < 0) throw new IllegalArgumentException("retrievalDurationMs 不能小于 0");
    }
}