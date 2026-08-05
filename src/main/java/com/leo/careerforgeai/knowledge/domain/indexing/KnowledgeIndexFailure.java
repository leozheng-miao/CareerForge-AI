package com.leo.careerforgeai.knowledge.domain.indexing;

/**
 * @program: CareerForge-AI
 * @description: 保存 Elasticsearch Bulk 中单个 Chunk 的失败信息
 * @author: Miao Zheng
 * @date: 2026-08-03 13:54
 **/
public record KnowledgeIndexFailure(
        String chunkId,
        int status,
        String errorType,
        String reason
) {
    public KnowledgeIndexFailure {
        if (chunkId == null || chunkId.isBlank()) throw new IllegalArgumentException("chunkId 不能为空");
        if (status < 400 || status > 599) throw new IllegalArgumentException("status 必须是失败状态码");
        if (errorType == null || errorType.isBlank()) throw new IllegalArgumentException("errorType 不能为空");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason 不能为空");
    }
}