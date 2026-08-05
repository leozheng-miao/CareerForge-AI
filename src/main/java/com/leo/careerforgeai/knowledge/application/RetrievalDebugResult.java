package com.leo.careerforgeai.knowledge.application;

import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;

/**
 * 保存一次检索调试的请求标识、完整混合检索结果和总耗时
 * @param requestId
 * @param retrievalResult
 * @param totalDurationMs
 */
public record RetrievalDebugResult(
        String requestId,
        HybridRetrievalResult retrievalResult,
        long totalDurationMs
) {

    public RetrievalDebugResult {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId 不能为空");
        if (retrievalResult == null) throw new IllegalArgumentException("retrievalResult 不能为空");
        if (totalDurationMs < 0) throw new IllegalArgumentException("totalDurationMs 不能小于 0");
    }
}