package com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.retrieval;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-03 22:23
 **/
public class KnowledgeRetrievalException extends RuntimeException {
    public KnowledgeRetrievalException(String message) {
        super(message);
    }

    public KnowledgeRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }}