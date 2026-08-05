package com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.indexing;

/**
 * @program: CareerForge-AI
 * @description: 表示整个 Elasticsearch Bulk 请求无法执行，或响应结构不可信
 * @author: Miao Zheng
 * @date: 2026-08-03 14:04
 **/
public class KnowledgeIndexException extends RuntimeException {
    public KnowledgeIndexException(String message) {
        super(message);
    }

    public KnowledgeIndexException(String message, Throwable cause) {
        super(message, cause);
    }}