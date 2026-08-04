package com.leo.careerforgeai.knowledge.domain.retrieval;

/**
 * @program: CareerForge-AI
 * @description: 保存同一个问题的 BM25、Vector 两路结果及 Query Embedding 信息
 * @author: Miao Zheng
 * @date: 2026-08-04 15:29
 **/
public record RetrievalComparisonResult(
        RetrievalResult bm25Result,
        RetrievalResult vectorResult,
        String embeddingModel,
        int embeddingDimensions,
        long queryEmbeddingDurationMs
) {

    public RetrievalComparisonResult {
        if (bm25Result == null) throw new IllegalArgumentException("bm25Result 不能为空");
        if (vectorResult == null) throw new IllegalArgumentException("vectorResult 不能为空");
        if (embeddingModel == null || embeddingModel.isBlank()) throw new IllegalArgumentException("embeddingModel 不能为空");
        if (embeddingDimensions <= 0) throw new IllegalArgumentException("embeddingDimensions 必须大于 0");
        if (queryEmbeddingDurationMs < 0) throw new IllegalArgumentException("queryEmbeddingDurationMs 不能小于 0");
    }

    /** 返回 Query Embedding 与 Elasticsearch kNN 的向量链路总耗时。 */
    public long vectorTotalDurationMs() {
        return queryEmbeddingDurationMs + vectorResult.durationMs();
    }
}