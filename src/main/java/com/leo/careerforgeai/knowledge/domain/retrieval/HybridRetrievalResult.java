package com.leo.careerforgeai.knowledge.domain.retrieval;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 同时保存双路原始召回结果、RRF 最终排名和融合耗时
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
public record HybridRetrievalResult(
        RetrievalComparisonResult comparisonResult,
        List<RrfRankedChunk> rrfChunks,
        long fusionDurationMs
) {

    public HybridRetrievalResult {
        if (comparisonResult == null) throw new IllegalArgumentException("comparisonResult 不能为空");
        if (rrfChunks == null) throw new IllegalArgumentException("rrfChunks 不能为空");
        if (rrfChunks.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("rrfChunks 不能包含 null");
        if (fusionDurationMs < 0) throw new IllegalArgumentException("fusionDurationMs 不能小于 0");
        rrfChunks = List.copyOf(rrfChunks);
        for (int index = 0; index < rrfChunks.size(); index++) {
            if (rrfChunks.get(index).finalRank() != index + 1) throw new IllegalArgumentException("RRF finalRank 必须连续且从 1 开始");
        }
    }
}