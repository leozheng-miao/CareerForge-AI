package com.leo.careerforgeai.model.exception.structured;

import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 携带稳定失败阶段、原因和安全字段路径的结构化输出异常
 * @author: Miao Zheng
 * @date: 2026-09-01
 */
public final class StructuredOutputException extends ModelException {

    private static final int MAX_FIELD_PATH_LENGTH = 256;

    private final StructuredOutputFailureStage failureStage;
    private final StructuredOutputFailureReason failureReason;
    private final String fieldPath;

    public StructuredOutputException(
            StructuredOutputFailureStage failureStage,
            StructuredOutputFailureReason failureReason,
            String fieldPath,
            String safeMessage,
            Throwable cause
    ) {
        super(ModelErrorType.STRUCTURED_OUTPUT_INVALID, safeMessage, cause);
        this.failureStage = Objects.requireNonNull(failureStage, "failureStage不能为空");
        this.failureReason = Objects.requireNonNull(failureReason, "failureReason不能为空");
        this.fieldPath = normalizeFieldPath(fieldPath);
    }

    public StructuredOutputFailureStage failureStage() {
        return failureStage;
    }

    public StructuredOutputFailureReason failureReason() {
        return failureReason;
    }

    public String fieldPath() {
        return fieldPath;
    }

    private static String normalizeFieldPath(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > MAX_FIELD_PATH_LENGTH
                || normalized.chars().anyMatch(Character::isISOControl)) {
            return "$.<redacted>";
        }
        return normalized;
    }
}