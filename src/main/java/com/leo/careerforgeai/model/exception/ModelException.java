package com.leo.careerforgeai.model.exception;

import com.leo.careerforgeai.shared.exception.BusinessException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import lombok.Getter;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 续兼容现有BaseResponse和GlobalExceptionHandler
 * @author: Miao Zheng
 * @date: 2026-07-30 16:38
 **/
@Getter
public class ModelException extends BusinessException {

    private final ModelErrorType errorType;

    public ModelException(ModelErrorType errorType, String message) {
        super(ErrorCode.SYSTEM_ERROR, message);
        this.errorType = Objects.requireNonNull(errorType);
    }

    public ModelException(ModelErrorType errorType, String message, Throwable cause) {
        super(ErrorCode.SYSTEM_ERROR, message);
        this.errorType = Objects.requireNonNull(errorType);
        if (cause != null) initCause(cause);
    }
}