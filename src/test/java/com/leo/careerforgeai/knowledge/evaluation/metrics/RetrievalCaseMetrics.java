package com.leo.careerforgeai.knowledge.evaluation.metrics;

public record RetrievalCaseMetrics(
        String caseId,
        int topK,
        int relevantCount,
        int retrievedCount,
        int hitCount,
        double recallAtK,
        double precisionAtK,
        double reciprocalRank,
        int firstRelevantRank
) {
}