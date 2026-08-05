package com.leo.careerforgeai.knowledge.evaluation.metrics;

public record RetrievalEvaluationSummary(
        RetrievalStrategy strategy,
        int topK,
        int evaluatedCases,
        double meanRecallAtK,
        double meanPrecisionAtK,
        double mrr,
        long retrievalLatencyP50Ms,
        long retrievalLatencyP95Ms
) {
}