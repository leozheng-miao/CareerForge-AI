package com.leo.careerforgeai.interview.api.advice;

import com.leo.careerforgeai.interview.api.report.InterviewReportController;
import com.leo.careerforgeai.interview.application.report.InterviewReportConfirmationException;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
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
 * @description: 将面试报告查询、确认、幂等和下游应用冲突映射为稳定API错误
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
@Slf4j
@RestControllerAdvice(assignableTypes = InterviewReportController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InterviewReportApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> handleUnreadable(HttpMessageNotReadableException exception) {
        log.warn("面试报告请求JSON无法读取，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求JSON格式或字段不合法");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public BaseResponse<?> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("面试报告路径参数错误，parameter={}", exception.getName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求路径参数格式不合法");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<?> handleInvalidArgument(IllegalArgumentException exception) {
        log.warn("面试报告请求参数错误，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, exception.getMessage());
    }

    @ExceptionHandler(MockInterviewNotFoundException.class)
    public BaseResponse<?> handleInterviewNotFound(MockInterviewNotFoundException exception) {
        log.warn("模拟面试不存在，interviewId={}", exception.interviewId());
        return ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "模拟面试不存在或不属于当前用户");
    }

    @ExceptionHandler(InterviewReportConfirmationException.class)
    public BaseResponse<?> handleConfirmationFailure(
            InterviewReportConfirmationException exception
    ) {
        log.warn("面试报告确认失败，reason={}", exception.reason());
        return switch (exception.reason()) {
            case REPORT_NOT_FOUND ->
                    ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "面试报告不存在或不属于当前用户");
            case CONFIRMATION_NOT_FOUND ->
                    ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "报告确认结果不存在");
            case REQUEST_CONFLICT ->
                    ResultUtils.error(ErrorCode.CONFLICT_ERROR, "requestId已被用于不同的确认请求");
            case REPORT_VERSION_CONFLICT ->
                    ResultUtils.error(ErrorCode.CONFLICT_ERROR, "报告版本已经变化，请刷新后重试");
            case REPORT_STATE_CONFLICT ->
                    ResultUtils.error(ErrorCode.CONFLICT_ERROR, "报告当前状态不允许确认");
            case APPLICATION_CONFLICT ->
                    ResultUtils.error(ErrorCode.CONFLICT_ERROR, "报告确认应用发生并发冲突，请刷新后重试");
        };
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public BaseResponse<?> handleIntegrityConflict(
            DataIntegrityViolationException exception
    ) {
        log.warn("面试报告数据库约束冲突，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.CONFLICT_ERROR, "报告确认发生并发冲突，请刷新后重试");
    }

    @ExceptionHandler({IllegalStateException.class, DataAccessException.class})
    public BaseResponse<?> handleInternalFailure(RuntimeException exception) {
        log.warn("面试报告状态或持久化失败，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "面试报告暂时无法处理，请稍后重试");
    }
}