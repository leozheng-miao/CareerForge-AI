package com.leo.careerforgeai.agent.api.advice;

import com.leo.careerforgeai.agent.api.CareerCoachController;
import com.leo.careerforgeai.agent.application.coach.CareerCoachExecutionException;
import com.leo.careerforgeai.agent.application.coach.validation.CareerCoachFinalAnswerException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.leo.careerforgeai.agent.api.CoachingSessionController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
/**
 * @program: CareerForge-AI
 * @description: 将Career Coach参数、运行终止和最终回答校验异常转换为安全API错误。
 * @author: Miao Zheng
 * @date: 2026-08-07 06:20
 **/
@RestControllerAdvice(assignableTypes = {
        CareerCoachController.class,
        CoachingSessionController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class CareerCoachApiExceptionHandler {

    /** 将非法JSON、未知字段和字段类型错误映射为参数错误。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        log.warn("Career Coach请求JSON无法读取，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求JSON格式或字段不合法");
    }

    /** 将服务层输入边界失败映射为参数错误。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<?> handleInvalidArgument(IllegalArgumentException exception) {
        log.warn("Career Coach请求参数错误，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, exception.getMessage());
    }

    /** 将Agent超时、预算、限制和模型故障映射为不泄露内部信息的操作错误。 */
    @ExceptionHandler(CareerCoachExecutionException.class)
    public BaseResponse<?> handleExecutionFailure(CareerCoachExecutionException exception) {
        log.warn("Career Coach执行未完成，runId={}, status={}, terminationReason={}",
                exception.getTrace().runId(), exception.getRunStatus(), exception.getTerminationReason());

        String safeMessage = switch (exception.getRunStatus()) {
            case TIMED_OUT -> "Career Coach请求超时，请稍后重试";
            case BUDGET_EXCEEDED, LIMIT_EXCEEDED -> "Career Coach达到运行限制，请缩小问题范围";
            case REFUSED -> "Career Coach拒绝处理当前请求";
            case FAILED -> "Career Coach暂时无法完成请求，请稍后重试";
            case COMPLETED -> "Career Coach运行状态异常";
        };
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, safeMessage);
    }

    /** 将模型最终结构或引用校验失败映射为安全操作错误。 */
    @ExceptionHandler(CareerCoachFinalAnswerException.class)
    public BaseResponse<?> handleFinalAnswerFailure(CareerCoachFinalAnswerException exception) {
        log.warn("Career Coach最终回答校验失败，errorType={}", exception.getErrorType());
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "Career Coach回答校验失败，请重新尝试");
    }

    /** 将非法UUID等路径参数转换为安全参数错误。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public BaseResponse<?> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        log.warn("Career Coach路径参数类型错误，parameter={}", exception.getName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求路径参数格式不合法");
    }

    /** 将关闭会话、过期版本和并发冲突转换为安全操作错误。 */
    @ExceptionHandler(IllegalStateException.class)
    public BaseResponse<?> handleInvalidState(IllegalStateException exception) {
        log.warn("Career Coach会话状态冲突，error={}", exception.getMessage());
        return ResultUtils.error(
                ErrorCode.OPERATION_ERROR,
                "会话状态或版本已经变化，请刷新后重试"
        );
    }
}