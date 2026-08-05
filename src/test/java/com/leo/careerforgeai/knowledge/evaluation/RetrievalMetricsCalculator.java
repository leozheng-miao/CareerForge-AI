package com.leo.careerforgeai.knowledge.evaluation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RetrievalMetricsCalculator {

    /** 计算一条可回答 Case 在指定 Top K 下的检索指标。 */
    public RetrievalCaseMetrics calculate(String caseId, Set<String> relevantChunkIds, List<String> rankedChunkIds, int topK) {
        if (caseId == null || caseId.isBlank()) throw new IllegalArgumentException("caseId 不能为空");
        if (relevantChunkIds == null || relevantChunkIds.isEmpty()) throw new IllegalArgumentException("可回答 Case 的 relevantChunkIds 不能为空");
        if (relevantChunkIds.stream().anyMatch(chunkId -> chunkId == null || chunkId.isBlank())) throw new IllegalArgumentException("relevantChunkIds 不能包含空值");
        if (rankedChunkIds == null) throw new IllegalArgumentException("rankedChunkIds 不能为空");
        if (rankedChunkIds.stream().anyMatch(chunkId -> chunkId == null || chunkId.isBlank())) throw new IllegalArgumentException("rankedChunkIds 不能包含空值");
        if (new HashSet<>(rankedChunkIds).size() != rankedChunkIds.size()) throw new IllegalArgumentException("rankedChunkIds 不能包含重复 Chunk ID");
        if (topK <= 0) throw new IllegalArgumentException("topK 必须大于 0");

        List<String> retrievedAtK = rankedChunkIds.stream().limit(topK).toList();
        int hitCount = 0;
        int firstRelevantRank = 0;

        for (int index = 0; index < retrievedAtK.size(); index++) {
            if (!relevantChunkIds.contains(retrievedAtK.get(index))) continue;
            hitCount++;
            if (firstRelevantRank == 0) firstRelevantRank = index + 1;
        }

        double recallAtK = (double) hitCount / relevantChunkIds.size();
        double precisionAtK = (double) hitCount / topK;
        double reciprocalRank = firstRelevantRank == 0 ? 0.0 : 1.0 / firstRelevantRank;

        return new RetrievalCaseMetrics(
                caseId,
                topK,
                relevantChunkIds.size(),
                retrievedAtK.size(),
                hitCount,
                recallAtK,
                precisionAtK,
                reciprocalRank,
                firstRelevantRank
        );
    }
}