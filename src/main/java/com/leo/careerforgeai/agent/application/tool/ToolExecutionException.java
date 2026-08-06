package com.leo.careerforgeai.agent.application.tool;

import com.leo.careerforgeai.agent.domain.tool.ToolExecutionErrorType;

import java.util.Objects;

/** 表示业务工具主动声明的、可安全分类的预期执行失败。 */
public final class ToolExecutionException extends RuntimeException {

    private final ToolExecutionErrorType errorType;

    public ToolExecutionException(
            ToolExecutionErrorType errorType,
            String safeMessage
    ) {
        this(errorType, safeMessage, null);
    }

    public ToolExecutionException(
            ToolExecutionErrorType errorType,
            String safeMessage,
            Throwable cause
    ) {
        super(validateSafeMessage(safeMessage), cause);
        this.errorType = Objects.requireNonNull(
                errorType,
                "errorType 不能为空"
        );
    }

    public ToolExecutionErrorType getErrorType() {
        return errorType;
    }

    private static String validateSafeMessage(String safeMessage) {
        if (safeMessage == null || safeMessage.isBlank()) {
            throw new IllegalArgumentException(
                    "safeMessage 不能为空"
            );
        }
        if (safeMessage.length() > 256) {
            throw new IllegalArgumentException(
                    "safeMessage 不能超过 256 个字符"
            );
        }
        return safeMessage;
    }
}