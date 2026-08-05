package com.leo.careerforgeai.knowledge.infrastructure.document.chunking;

/**
 * @program: CareerForge-AI
 * @description: 明确表示文档已经成功读取，但当前 Chunking 策略无法安全处理
 * @author: Miao Zheng
 * @date: 2026-07-31 22:18
 **/
public class DocumentChunkingException extends RuntimeException {
    public DocumentChunkingException(String message) {
        super(message);
    }
}