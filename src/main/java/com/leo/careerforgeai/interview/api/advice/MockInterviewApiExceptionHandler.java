package com.leo.careerforgeai.interview.api.advice;

import com.leo.careerforgeai.interview.api.session.MockInterviewController;
import com.leo.careerforgeai.interview.application.session.MockInterviewRequestConflictException;
import com.leo.careerforgeai.interview.application.snapshot.MockInterviewInputConflictException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @program: CareerForge-AI
 * @description: 将模拟面试创建参数、输入版本和幂等冲突映射为稳定API错误
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@Slf4j
@RestControllerAdvice(assignableTypes = MockInterviewController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MockInterviewApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> handleUnreadable(HttpMessageNotReadableException exception) {
        log.warn("模拟面试创建请求JSON无法读取，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求JSON格式或字段不合法");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<?> handleInvalidArgument(IllegalArgumentException exception) {
        log.warn("模拟面试创建参数错误，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, exception.getMessage());
    }

    @ExceptionHandler(MockInterviewInputConflictException.class)
    public BaseResponse<?> handleInputConflict(MockInterviewInputConflictException exception) {
        log.warn("模拟面试输入版本冲突，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.CONFLICT_ERROR, "所选岗位、训练计划或个人证据版本已经变化");
    }

    @ExceptionHandler(MockInterviewRequestConflictException.class)
    public BaseResponse<?> handleRequestConflict(MockInterviewRequestConflictException exception) {
        log.warn("模拟面试创建请求冲突，existingInterviewId={}", exception.existingInterviewId());
        return ResultUtils.error(ErrorCode.CONFLICT_ERROR, "requestId已被用于不同的模拟面试创建请求");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public BaseResponse<?> handleIntegrityConflict(DataIntegrityViolationException exception) {
        log.warn("模拟面试创建数据库约束冲突，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.CONFLICT_ERROR, "模拟面试创建发生并发冲突，请重试");
    }

    @ExceptionHandler({IllegalStateException.class, DataAccessException.class})
    public BaseResponse<?> handleInternalFailure(RuntimeException exception) {
        log.error(
                "模拟面试创建失败，exceptionType={}, error={}",
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                exception
        );        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "模拟面试暂时无法创建，请稍后重试");
    }
}