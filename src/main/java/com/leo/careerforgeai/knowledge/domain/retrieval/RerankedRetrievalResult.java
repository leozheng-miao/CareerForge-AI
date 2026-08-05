package com.leo.careerforgeai.knowledge.domain.retrieval;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 保存完整混合召回证据、最终有效顺序、Rerank 状态和耗时
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
public record RerankedRetrievalResult(
        HybridRetrievalResult hybridResult,
        List<RrfRankedChunk> rankedChunks,
        RerankStatus status,
        long rerankDurationMs
) {

    public RerankedRetrievalResult {
        if (hybridResult == null) throw new IllegalArgumentException("hybridResult 不能为空");
        if (rankedChunks == null) throw new IllegalArgumentException("rankedChunks 不能为空");
        if (rankedChunks.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("rankedChunks 不能包含 null");
        if (status == null) throw new IllegalArgumentException("status 不能为空");
        if (rerankDurationMs < 0) throw new IllegalArgumentException("rerankDurationMs 不能小于 0");
        rankedChunks = List.copyOf(rankedChunks);

        Set<String> originalIds = new HashSet<>();
        hybridResult.rrfChunks().forEach(candidate -> originalIds.add(candidate.chunk().chunkId()));
        Set<String> rankedIds = new HashSet<>();
        rankedChunks.forEach(candidate -> rankedIds.add(candidate.chunk().chunkId()));

        if (rankedIds.size() != rankedChunks.size()) throw new IllegalArgumentException("rankedChunks 包含重复 Chunk ID");
        if (!rankedIds.equals(originalIds)) throw new IllegalArgumentException("rankedChunks 必须与 RRF 候选集合完全一致");
    }
}