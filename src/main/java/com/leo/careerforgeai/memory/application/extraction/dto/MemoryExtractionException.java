package com.leo.careerforgeai.memory.application.extraction.dto;

import com.leo.careerforgeai.model.domain.ModelUsage;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 表示Memory提取无法通过输入、模型调用或Java可信边界校验
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
public final class MemoryExtractionException extends RuntimeException {

    private final MemoryExtractionErrorType errorType;
    private final MemoryExtractionFailureStage failureStage;
    private final String modelRequestId;
    private final ModelUsage modelUsage;
    private final long modelDurationMs;
    private final int modelCallCount;

    public MemoryExtractionException(
            MemoryExtractionErrorType errorType,
            MemoryExtractionFailureStage failureStage,
            String safeMessage,
            Throwable cause,
            String modelRequestId,
            ModelUsage modelUsage,
            long modelDurationMs,
            int modelCallCount
    ) {
        super(safeMessage, cause);
        this.errorType = Objects.requireNonNull(errorType, "errorType不能为空");
        this.failureStage = Objects.requireNonNull(failureStage, "failureStage不能为空");
        this.modelRequestId = normalizeOptional(modelRequestId);
        this.modelUsage = modelUsage;

        if (modelUsage != null && (modelUsage.inputTokens() < 0
                || modelUsage.outputTokens() < 0
                || modelUsage.totalTokens() < 0)) {
            throw new IllegalArgumentException("modelUsage不能包含负数");
        }
        if (modelDurationMs < 0) {
            throw new IllegalArgumentException("modelDurationMs不能小于0");
        }
        if (modelCallCount < 0) {
            throw new IllegalArgumentException("modelCallCount不能小于0");
        }

        this.modelDurationMs = modelDurationMs;
        this.modelCallCount = modelCallCount;
    }

    public MemoryExtractionException withModelMetrics(
            ModelUsage aggregatedUsage,
            long aggregatedDurationMs,
            int aggregatedModelCallCount
    ) {
        return new MemoryExtractionException(
                errorType,
                failureStage,
                getMessage(),
                getCause(),
                modelRequestId,
                aggregatedUsage,
                aggregatedDurationMs,
                aggregatedModelCallCount
        );
    }

    public MemoryExtractionErrorType getErrorType() {
        return errorType;
    }

    public MemoryExtractionFailureStage getFailureStage() {
        return failureStage;
    }

    public String getModelRequestId() {
        return modelRequestId;
    }

    public ModelUsage getModelUsage() {
        return modelUsage;
    }

    public long getModelDurationMs() {
        return modelDurationMs;
    }

    public int getModelCallCount() {
        return modelCallCount;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}