package com.leo.careerforgeai.model.exception;

import com.leo.careerforgeai.shared.exception.BusinessException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import lombok.Getter;

import java.time.Duration;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 统一模型异常并携带可选的供应商重试等待时间
 * @author: Miao Zheng
 * @date: 2026-08-24
 **/
@Getter
public class ModelException extends BusinessException {

    private final ModelErrorType errorType;
    private final Duration retryAfter;

    public ModelException(ModelErrorType errorType, String message) {
        this(errorType, message, null, null);
    }

    public ModelException(ModelErrorType errorType, String message, Throwable cause) {
        this(errorType, message, null, cause);
    }

    public ModelException(ModelErrorType errorType, String message, Duration retryAfter) {
        this(errorType, message, retryAfter, null);
    }

    private ModelException(ModelErrorType errorType, String message, Duration retryAfter, Throwable cause) {
        super(ErrorCode.SYSTEM_ERROR, message);
        this.errorType = Objects.requireNonNull(errorType);
        if (retryAfter != null && errorType != ModelErrorType.RATE_LIMITED) {
            throw new IllegalArgumentException("retryAfter只能用于RATE_LIMITED错误");
        }
        if (retryAfter != null && retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter不能小于0");
        }
        this.retryAfter = retryAfter;
        if (cause != null) initCause(cause);
    }
}