package com.leo.careerforgeai.knowledge.domain.retrieval;

import com.leo.careerforgeai.knowledge.domain.DocumentChunk;

/**
 * @program: CareerForge-AI
 * @description: 统一表示 BM25 或向量检索返回的一个已排名 Chunk
 * @author: Miao Zheng
 * @date: 2026-08-03 22:16
 **/
public record RetrievedChunk(
        DocumentChunk chunk,
        double score,
        int rank
) {

    public RetrievedChunk {
        if (chunk == null) throw new IllegalArgumentException("chunk 不能为空");
        if (!Double.isFinite(score)) throw new IllegalArgumentException("score 必须是有限数值");
        if (rank <= 0) throw new IllegalArgumentException("rank 必须从 1 开始");
    }
}