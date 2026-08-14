package com.leo.careerforgeai.memory.api.advice;

import com.leo.careerforgeai.memory.api.MemoryCandidateController;
import com.leo.careerforgeai.memory.application.extraction.dto.MemoryExtractionException;
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
 * @description: 将Memory候选提取的输入和模型失败转换为不泄露内部信息的API错误
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
@RestControllerAdvice(assignableTypes = MemoryCandidateController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class MemoryCandidateApiExceptionHandler {

    /** 将非法JSON、未知字段和字段类型错误转换为参数错误。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        log.warn("Memory候选提取请求JSON无法读取，exceptionType={}",
                exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求JSON格式或字段不合法");
    }

    /** 将空列表、重复Turn和owner或Session边界失败转换为安全参数错误。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<?> handleInvalidArgument(IllegalArgumentException exception) {
        log.warn("Memory候选提取参数错误，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, exception.getMessage());
    }

    /** 将非法UUID等路径参数转换为参数错误。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public BaseResponse<?> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("Memory候选提取路径参数错误，parameter={}", exception.getName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求路径参数格式不合法");
    }

    /** 将模型调用和不可信模型输出失败转换为稳定错误，不伪装为空候选。 */
    @ExceptionHandler(MemoryExtractionException.class)
    public BaseResponse<?> handleExtractionFailure(MemoryExtractionException exception) {
        log.warn(
                "Memory候选提取失败，errorType={}, failureStage={}, modelRequestId={}, "
                        + "modelCallCount={}, modelDurationMs={}",
                exception.getErrorType(),
                exception.getFailureStage(),
                exception.getModelRequestId(),
                exception.getModelCallCount(),
                exception.getModelDurationMs()
        );
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "Memory候选提取失败，请稍后重试");
    }
}