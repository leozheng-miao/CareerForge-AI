package com.leo.careerforgeai.knowledge.application.answer;

/**
 * @program: CareerForge-AI
 * @description: 表示 RAG 回答模型调用失败或结构化回答不可信
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
public class RagAnswerException extends RuntimeException {

    public RagAnswerException(String message) {
        super(message);
    }

    public RagAnswerException(String message, Throwable cause) {
        super(message, cause);
    }
}