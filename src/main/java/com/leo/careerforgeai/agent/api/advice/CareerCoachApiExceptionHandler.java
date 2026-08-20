package com.leo.careerforgeai.agent.api.advice;

import com.leo.careerforgeai.agent.api.CareerCoachController;
import com.leo.careerforgeai.agent.api.CoachingRunController;
import com.leo.careerforgeai.agent.api.CoachingSessionController;
import com.leo.careerforgeai.agent.application.coach.CareerCoachExecutionException;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerException;
import com.leo.careerforgeai.agent.application.run.CoachingRunNotFoundException;
import com.leo.careerforgeai.agent.application.run.CoachingRunRequestConflictException;
import com.leo.careerforgeai.agent.application.run.CoachingRunVersionConflictException;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunCapacityRejectedException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * @program: CareerForge-AI
 * @description: 将Career Coach和Coaching Run异常转换为安全稳定的API错误
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@RestControllerAdvice(assignableTypes = {
        CareerCoachController.class,
        CoachingSessionController.class,
        CoachingRunController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class CareerCoachApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        log.warn("Career Coach请求JSON无法读取，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求JSON格式或字段不合法");
    }

    @ExceptionHandler(CoachingRunRequestConflictException.class)
    public ResponseEntity<BaseResponse<?>> handleRequestConflict(
            CoachingRunRequestConflictException exception
    ) {
        log.warn("Coaching Run请求指纹冲突，runId={}", exception.existingRunId());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ResultUtils.error(ErrorCode.CONFLICT_ERROR, "requestId已被用于不同请求"));
    }

    @ExceptionHandler(CoachingRunVersionConflictException.class)
    public ResponseEntity<BaseResponse<?>> handleVersionConflict(
            CoachingRunVersionConflictException exception
    ) {
        log.warn("Coaching Run版本冲突，runId={}, expectedVersion={}", exception.runId(), exception.expectedVersion());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ResultUtils.error(ErrorCode.CONFLICT_ERROR, "Run状态已经变化，请重新查询"));
    }

    @ExceptionHandler(CoachingRunCapacityRejectedException.class)
    public ResponseEntity<BaseResponse<?>> handleCapacityRejected(
            CoachingRunCapacityRejectedException exception
    ) {
        log.warn("Coaching Run执行容量已满，ownerId={}", exception.ownerId().value());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ResultUtils.error(ErrorCode.TOO_MANY_REQUEST, "当前Run执行容量已满，请稍后重试"));
    }

    @ExceptionHandler(CoachingRunNotFoundException.class)
    public ResponseEntity<BaseResponse<?>> handleRunNotFound(
            CoachingRunNotFoundException exception
    ) {
        log.warn("Coaching Run不存在或不可访问，runId={}", exception.runId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "Run不存在或不属于当前用户"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<?> handleInvalidArgument(IllegalArgumentException exception) {
        log.warn("Career Coach请求参数错误，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, exception.getMessage());
    }

    @ExceptionHandler(CareerCoachExecutionException.class)
    public BaseResponse<?> handleExecutionFailure(CareerCoachExecutionException exception) {
        log.warn(
                "Career Coach执行未完成，runId={}, status={}, terminationReason={}",
                exception.getTrace().runId(),
                exception.getRunStatus(),
                exception.getTerminationReason()
        );

        String safeMessage = switch (exception.getRunStatus()) {
            case TIMED_OUT -> "Career Coach请求超时，请稍后重试";
            case BUDGET_EXCEEDED, LIMIT_EXCEEDED -> "Career Coach达到运行限制，请缩小问题范围";
            case REFUSED -> "Career Coach拒绝处理当前请求";
            case FAILED -> "Career Coach暂时无法完成请求，请稍后重试";
            case COMPLETED -> "Career Coach运行状态异常";
        };
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, safeMessage);
    }

    @ExceptionHandler(CareerCoachFinalAnswerException.class)
    public BaseResponse<?> handleFinalAnswerFailure(CareerCoachFinalAnswerException exception) {
        log.warn("Career Coach最终回答校验失败，errorType={}", exception.getErrorType());
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "Career Coach回答校验失败，请重新尝试");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public BaseResponse<?> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("Career Coach路径参数类型错误，parameter={}", exception.getName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求路径参数格式不合法");
    }

    @ExceptionHandler(IllegalStateException.class)
    public BaseResponse<?> handleInvalidState(IllegalStateException exception) {
        log.warn("Career Coach状态冲突，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "会话或Run状态已经变化，请刷新后重试");
    }
}