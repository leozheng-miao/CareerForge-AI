package com.leo.careerforgeai.career.application.training;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 区分训练计划固定输入读取阶段的业务失败语义
 * @author: Miao Zheng
 * @date: 2026-08-17
 */
public final class TrainingPlanGenerationException extends RuntimeException {

    private final ErrorType errorType;

    public TrainingPlanGenerationException(ErrorType errorType, String message) {
        super(message);
        this.errorType = Objects.requireNonNull(errorType, "errorType不能为空");
    }

    public TrainingPlanGenerationException(ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = Objects.requireNonNull(errorType, "errorType不能为空");
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * @program: CareerForge-AI
     * @description: 训练计划固定输入读取失败类型
     * @author: Miao Zheng
     * @date: 2026-08-17
     */
    public enum ErrorType {
        GAP_SNAPSHOT_NOT_FOUND,
        INPUT_VERSION_CONFLICT,
        INPUT_INTEGRITY_VIOLATION,
        TIME_CONSTRAINT_MISSING,
        TIME_CONSTRAINT_INVALID,
        CONTROLLED_RESOURCE_INVALID,
        MODEL_CALL_FAILED,
        MODEL_OUTPUT_INVALID,
        PERSISTENCE_FAILED
    }
}