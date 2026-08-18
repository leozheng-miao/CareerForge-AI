package com.leo.careerforgeai.career.api.advice;

import com.leo.careerforgeai.career.api.training.TrainingPlanController;
import com.leo.careerforgeai.career.application.training.TrainingPlanGenerationException;
import com.leo.careerforgeai.career.application.training.TrainingPlanVersionConflictException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * @program: CareerForge-AI
 * @description: 将训练计划输入、模型、安全校验和持久化失败映射为稳定API错误
 * @author: Miao Zheng
 * @date: 2026-08-18
 */
@RestControllerAdvice(assignableTypes = TrainingPlanController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class TrainingPlanApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> handleUnreadable(HttpMessageNotReadableException exception) {
        log.warn("训练计划请求JSON无法读取，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求JSON格式或字段不合法");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public BaseResponse<?> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("训练计划路径参数错误，parameter={}", exception.getName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求路径参数格式不合法");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<?> handleInvalidArgument(IllegalArgumentException exception) {
        log.warn("训练计划参数错误，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, exception.getMessage());
    }

    @ExceptionHandler(TrainingPlanGenerationException.class)
    public BaseResponse<?> handleGenerationFailure(TrainingPlanGenerationException exception) {
        log.warn("训练计划生成失败，errorType={}", exception.getErrorType());
        return switch (exception.getErrorType()) {
            case GAP_SNAPSHOT_NOT_FOUND ->
                    ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "能力差距快照不存在或不属于当前用户");
            case INPUT_VERSION_CONFLICT ->
                    ResultUtils.error(ErrorCode.OPERATION_ERROR, "计划输入已经变化，请刷新后重新生成");
            case TIME_CONSTRAINT_MISSING ->
                    ResultUtils.error(ErrorCode.OPERATION_ERROR, "请先确认每周可用学习时间");
            case TIME_CONSTRAINT_INVALID ->
                    ResultUtils.error(ErrorCode.OPERATION_ERROR, "已确认的每周可用时间无法安全解析");
            case MODEL_CALL_FAILED ->
                    ResultUtils.error(ErrorCode.OPERATION_ERROR, "训练计划模型调用失败，请稍后重试");
            case MODEL_OUTPUT_INVALID ->
                    ResultUtils.error(ErrorCode.OPERATION_ERROR, "训练计划草案未通过安全校验，请重新生成");
            case PERSISTENCE_FAILED ->
                    ResultUtils.error(ErrorCode.OPERATION_ERROR, "训练计划保存失败，请稍后重试");
            case CONTROLLED_RESOURCE_INVALID ->
                    ResultUtils.error(ErrorCode.OPERATION_ERROR, "受控训练资源暂时不可用");
            case INPUT_INTEGRITY_VIOLATION ->
                    ResultUtils.error(ErrorCode.OPERATION_ERROR, "训练计划输入完整性校验失败");
        };
    }

    @ExceptionHandler({IllegalStateException.class, DataAccessException.class})
    public BaseResponse<?> handleInternalState(RuntimeException exception) {
        log.warn("训练计划状态或持久化失败，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "训练计划暂时无法处理，请稍后重试");
    }

    @ExceptionHandler(TrainingPlanVersionConflictException.class)
    public BaseResponse<?> handleVersionConflict(TrainingPlanVersionConflictException exception) {
        log.warn("训练计划版本冲突，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "训练计划版本已经变化，请刷新后重试");
    }
}