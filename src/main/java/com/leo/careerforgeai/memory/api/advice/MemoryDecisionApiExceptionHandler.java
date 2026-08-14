package com.leo.careerforgeai.memory.api.advice;

import com.leo.careerforgeai.memory.api.MemoryDecisionController;
import com.leo.careerforgeai.memory.domain.profile.MemoryTransitionException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * @program: CareerForge-AI
 * @description: 将Memory确认和拒绝的输入、状态及版本失败转换为安全稳定的API响应
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
@RestControllerAdvice(assignableTypes = MemoryDecisionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class MemoryDecisionApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        log.warn("Memory决策请求JSON无法读取，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求JSON格式或字段不合法");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public BaseResponse<?> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("Memory决策路径参数错误，parameter={}", exception.getName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求路径参数格式不合法");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<?> handleInvalidArgument(IllegalArgumentException exception) {
        log.warn("Memory决策参数或来源校验失败，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, exception.getMessage());
    }

    @ExceptionHandler(MemoryTransitionException.class)
    public BaseResponse<?> handleInvalidTransition(MemoryTransitionException exception) {
        log.warn(
                "Memory状态不允许当前决策，status={}, decision={}",
                exception.currentStatus(),
                exception.decisionType()
        );
        return ResultUtils.error(
                ErrorCode.OPERATION_ERROR,
                "Memory当前状态不允许执行该决策"
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public BaseResponse<?> handleStateConflict(IllegalStateException exception) {
        log.warn("Memory决策状态或版本冲突，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, exception.getMessage());
    }
}