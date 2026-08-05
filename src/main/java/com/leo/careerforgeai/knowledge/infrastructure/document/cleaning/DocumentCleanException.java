package com.leo.careerforgeai.knowledge.infrastructure.document.cleaning;

import lombok.Getter;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 将文档清洗失败与读取、切分等其他 RAG 阶段区分开
 * @author: Miao Zheng
 * @date: 2026-07-31 16:24
 **/
@Getter
public class DocumentCleanException extends RuntimeException {

    private final DocumentCleanErrorType errorType;

    public DocumentCleanException(DocumentCleanErrorType errorType, String message) {
        super(message);
        this.errorType = Objects.requireNonNull(errorType);
    }
}