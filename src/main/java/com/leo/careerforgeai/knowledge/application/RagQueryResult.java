package com.leo.careerforgeai.knowledge.application;

import com.leo.careerforgeai.knowledge.domain.answer.RagAnswer;
import com.leo.careerforgeai.knowledge.domain.context.AssembledContext;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RerankedRetrievalResult;

/**
 * @program: CareerForge-AI
 * @description: 保存一次完整 RAG 查询的检索、重排、上下文、回答和总耗时
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
public record RagQueryResult(
        String requestId,
        HybridRetrievalResult retrievalResult,
        RerankedRetrievalResult rerankedResult,
        AssembledContext context,
        RagAnswer answer,
        long totalDurationMs
) {

    public RagQueryResult {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId 不能为空");
        if (retrievalResult == null) throw new IllegalArgumentException("retrievalResult 不能为空");
        if (rerankedResult == null) throw new IllegalArgumentException("rerankedResult 不能为空");
        if (context == null) throw new IllegalArgumentException("context 不能为空");
        if (answer == null) throw new IllegalArgumentException("answer 不能为空");
        if (totalDurationMs < 0) throw new IllegalArgumentException("totalDurationMs 不能小于 0");
    }
}