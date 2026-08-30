package com.leo.careerforgeai.interview.api.advice;

import com.leo.careerforgeai.interview.api.controller.PersonalEvidenceController;
import com.leo.careerforgeai.interview.application.evidence.PersonalEvidenceNotFoundException;
import com.leo.careerforgeai.interview.application.evidence.PersonalEvidenceVersionConflictException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * @program: CareerForge-AI
 * @description: 将个人证据参数、owner边界、版本竞争和持久化失败映射为稳定API错误
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@RestControllerAdvice(assignableTypes = PersonalEvidenceController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class PersonalEvidenceApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> handleUnreadable(HttpMessageNotReadableException exception) {
        log.warn("个人证据请求JSON无法读取，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求JSON格式或字段不合法");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public BaseResponse<?> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("个人证据路径参数错误，parameter={}", exception.getName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求路径参数格式不合法");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<?> handleInvalidArgument(IllegalArgumentException exception) {
        log.warn("个人证据参数错误，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, exception.getMessage());
    }

    @ExceptionHandler(PersonalEvidenceNotFoundException.class)
    public BaseResponse<?> handleNotFound(PersonalEvidenceNotFoundException exception) {
        log.warn("个人证据不存在，artifactId={}", exception.artifactId());
        return ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "个人证据不存在或不属于当前用户");
    }

    @ExceptionHandler(PersonalEvidenceVersionConflictException.class)
    public BaseResponse<?> handleVersionConflict(PersonalEvidenceVersionConflictException exception) {
        log.warn(
                "个人证据版本冲突，artifactId={}, expectedVersion={}",
                exception.artifactId(),
                exception.expectedVersion()
        );
        return ResultUtils.error(ErrorCode.CONFLICT_ERROR, "个人证据版本已经变化，请刷新后重试");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public BaseResponse<?> handleIntegrityConflict(DataIntegrityViolationException exception) {
        log.warn("个人证据数据库约束冲突，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.CONFLICT_ERROR, "个人证据发生并发冲突，请刷新后重试");
    }

    @ExceptionHandler({IllegalStateException.class, DataAccessException.class})
    public BaseResponse<?> handleInternalFailure(RuntimeException exception) {
        log.warn("个人证据状态或持久化失败，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "个人证据暂时无法处理，请稍后重试");
    }
}