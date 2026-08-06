package com.leo.careerforgeai.knowledge.application.evidence;

import com.leo.careerforgeai.knowledge.domain.context.AssembledContext;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankedRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 保存证据搜索的检索、重排、上下文和耗时结果，不包含模型生成的最终回答。
 * @author: Miao Zheng
 * @date: 2026-08-06 19:20
 **/
public record KnowledgeEvidenceSearchResult(
        String requestId,
        HybridRetrievalResult retrievalResult,
        RerankedRetrievalResult rerankedResult,
        AssembledContext context,
        long totalDurationMs
) {

    public KnowledgeEvidenceSearchResult {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId 不能为空");
        Objects.requireNonNull(retrievalResult, "retrievalResult 不能为空");
        Objects.requireNonNull(rerankedResult, "rerankedResult 不能为空");
        Objects.requireNonNull(context, "context 不能为空");
        if (totalDurationMs < 0) throw new IllegalArgumentException("totalDurationMs 不能小于 0");
    }

    /** 返回经过RRF融合后、进入可选重排前的候选数量。 */
    public int candidateCount() {
        return retrievalResult.rrfChunks().size();
    }
}