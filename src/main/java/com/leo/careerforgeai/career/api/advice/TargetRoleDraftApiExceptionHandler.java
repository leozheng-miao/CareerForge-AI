package com.leo.careerforgeai.career.api.advice;

import com.leo.careerforgeai.career.api.targetrole.TargetRoleController;
import com.leo.careerforgeai.career.api.targetrole.TargetRoleDraftController;
import com.leo.careerforgeai.career.application.requirement.JobRequirementsParseException;
import com.leo.careerforgeai.career.application.targetrole.TargetRoleVersionConflictException;
import com.leo.careerforgeai.model.exception.ModelErrorType;
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
 * @description: 将目标岗位草案输入、模型和持久化失败映射为安全稳定的API错误
 * @author: Miao Zheng
 * @date: 2026-08-16
 */
@RestControllerAdvice(assignableTypes = {
        TargetRoleDraftController.class,
        TargetRoleController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class TargetRoleDraftApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> handleUnreadable(
            HttpMessageNotReadableException exception
    ) {
        log.warn("目标岗位草案请求JSON无法读取，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求JSON格式或字段不合法");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public BaseResponse<?> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        log.warn("目标岗位草案路径参数错误，parameter={}", exception.getName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求路径参数格式不合法");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<?> handleInvalidArgument(
            IllegalArgumentException exception
    ) {
        log.warn("目标岗位草案参数错误，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, exception.getMessage());
    }

    @ExceptionHandler(JobRequirementsParseException.class)
    public BaseResponse<?> handleParseFailure(
            JobRequirementsParseException exception
    ) {
        log.warn("目标岗位草案解析失败，errorType={}, modelDurationMs={}", exception.getErrorType(), exception.getModelDurationMs());

        String message =
                exception.getErrorType() == ModelErrorType.TIMEOUT
                        ? "岗位要求解析超时，请稍后重试"
                        : "岗位要求解析失败，请稍后重试";

        return ResultUtils.error(ErrorCode.OPERATION_ERROR, message);
    }

    @ExceptionHandler({
            IllegalStateException.class,
            DataAccessException.class
    })
    public BaseResponse<?> handleInternalState(
            RuntimeException exception
    ) {
        log.warn("目标岗位草案状态或持久化失败，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "目标岗位草案暂时无法处理，请稍后重试");
    }

    @ExceptionHandler(TargetRoleVersionConflictException.class)
    public BaseResponse<?> handleVersionConflict(
            TargetRoleVersionConflictException exception
    ) {
        log.warn("目标岗位草案确认版本冲突，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public BaseResponse<?> handleIntegrityConflict(
            DataIntegrityViolationException exception
    ) {
        log.warn("目标岗位版本发生数据库约束冲突，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "目标岗位版本发生并发冲突，请刷新后重试");
    }
}