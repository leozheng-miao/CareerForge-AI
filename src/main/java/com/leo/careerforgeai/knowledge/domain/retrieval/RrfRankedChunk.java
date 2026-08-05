package com.leo.careerforgeai.knowledge.domain.retrieval;

import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;

/**
 * @program: CareerForge-AI
 * @description: 保存一个 Chunk 在 BM25、Vector 两路中的原始排名以及最终 RRF 排名
 * @author: Miao Zheng
 * @date: 2026-08-04
 **/
public record RrfRankedChunk(
        DocumentChunk chunk,
        Integer bm25Rank,
        Integer vectorRank,
        double rrfScore,
        int finalRank
) {

    public RrfRankedChunk {
        if (chunk == null) throw new IllegalArgumentException("chunk 不能为空");
        if (bm25Rank == null && vectorRank == null) throw new IllegalArgumentException("bm25Rank 和 vectorRank 不能同时为空");
        if (bm25Rank != null && bm25Rank <= 0) throw new IllegalArgumentException("bm25Rank 必须大于 0");
        if (vectorRank != null && vectorRank <= 0) throw new IllegalArgumentException("vectorRank 必须大于 0");
        if (!Double.isFinite(rrfScore) || rrfScore <= 0) throw new IllegalArgumentException("rrfScore 必须是大于 0 的有限数值");
        if (finalRank <= 0) throw new IllegalArgumentException("finalRank 必须大于 0");
    }
}