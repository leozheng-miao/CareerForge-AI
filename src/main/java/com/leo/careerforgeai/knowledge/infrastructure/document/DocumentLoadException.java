package com.leo.careerforgeai.knowledge.infrastructure.document;

import lombok.Getter;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 携带文档加载错误类型及原始异常原因
 * @author: Miao Zheng
 * @date: 2026-07-31 15:21
 **/
@Getter
public class DocumentLoadException extends RuntimeException {
    private final DocumentLoadErrorType errorType;

    public DocumentLoadException(DocumentLoadErrorType errorType, String message) {
        super(message);
        this.errorType = Objects.requireNonNull(errorType);
    }

    public DocumentLoadException(
            DocumentLoadErrorType errorType,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.errorType = Objects.requireNonNull(errorType);
    }
}