package com.leo.careerforgeai.knowledge.domain.retrieval;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 保存一次检索返回的有序 Chunk 和客户端总耗时
 * @author: Miao Zheng
 * @date: 2026-08-03 22:20
 **/
public record RetrievalResult(
        List<RetrievedChunk> chunks,
        long durationMs
) {

    public RetrievalResult {
        if (chunks == null) throw new IllegalArgumentException("chunks 不能为空");
        if (durationMs < 0) throw new IllegalArgumentException("durationMs 不能小于 0");
        chunks = List.copyOf(chunks);
        for (int index = 0; index < chunks.size(); index++) {
            if (chunks.get(index).rank() != index + 1) throw new IllegalArgumentException("RetrievedChunk rank 必须连续且从 1 开始");
        }
    }
}