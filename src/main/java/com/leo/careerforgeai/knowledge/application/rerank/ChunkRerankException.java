package com.leo.careerforgeai.knowledge.application.rerank;

/**
 * @program: CareerForge-AI
 * @description: 表示 LLM Rerank 调用失败或结构化排序结果不可信
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
public class ChunkRerankException extends RuntimeException {

    public ChunkRerankException(String message) {
        super(message);
    }

    public ChunkRerankException(String message, Throwable cause) {
        super(message, cause);
    }
}