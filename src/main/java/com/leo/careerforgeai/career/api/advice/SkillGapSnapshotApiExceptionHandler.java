package com.leo.careerforgeai.career.api.advice;

import com.leo.careerforgeai.career.api.SkillGapSnapshotController;
import com.leo.careerforgeai.career.application.SkillGapInputVersionConflictException;
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
 * @description: 将能力差距输入、版本、安全边界和持久化失败映射为稳定API错误
 * @author: Miao Zheng
 * @date: 2026-08-17
 */
@RestControllerAdvice(assignableTypes = SkillGapSnapshotController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class SkillGapSnapshotApiExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> handleUnreadable(HttpMessageNotReadableException exception) {
        log.warn("能力差距请求JSON无法读取，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求JSON格式或字段不合法");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public BaseResponse<?> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("能力差距路径参数错误，parameter={}", exception.getName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求路径参数格式不合法");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<?> handleInvalidArgument(IllegalArgumentException exception) {
        log.warn("能力差距参数错误，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, exception.getMessage());
    }

    @ExceptionHandler(SkillGapInputVersionConflictException.class)
    public BaseResponse<?> handleVersionConflict(SkillGapInputVersionConflictException exception) {
        log.warn("能力差距输入版本冲突，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public BaseResponse<?> handleIntegrityConflict(DataIntegrityViolationException exception) {
        log.warn("能力差距快照发生数据库约束冲突，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "能力差距快照发生并发冲突，请刷新后重试");
    }

    @ExceptionHandler({IllegalStateException.class, DataAccessException.class})
    public BaseResponse<?> handleInternalState(RuntimeException exception) {
        log.warn("能力差距安全边界或持久化失败，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "能力差距暂时无法处理，请稍后重试");
    }
}