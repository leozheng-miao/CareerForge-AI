package com.leo.careerforgeai.interview.api.advice;

import com.leo.careerforgeai.agent.application.run.execution.CoachingRunCapacityRejectedException;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunDispatchRejectedException;
import com.leo.careerforgeai.agent.application.run.execution.RunExecutionDeadlineExceededException;
import com.leo.careerforgeai.interview.api.controller.InterviewEventController;
import com.leo.careerforgeai.interview.api.controller.MockInterviewController;
import com.leo.careerforgeai.interview.application.session.MockInterviewCancellationConflictException;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.application.session.MockInterviewRequestConflictException;
import com.leo.careerforgeai.interview.application.session.MockInterviewVersionConflictException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.leo.careerforgeai.interview.api.controller.InterviewQuestionController;
import com.leo.careerforgeai.interview.application.question.CurrentInterviewQuestionUnavailableException;
import com.leo.careerforgeai.interview.api.controller.InterviewAnswerController;

/**
 * @program: CareerForge-AI
 * @description: 将模拟面试创建、查询、异步准入和版本冲突映射为稳定API错误
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@Slf4j
@RestControllerAdvice(assignableTypes = {
        MockInterviewController.class,
        InterviewQuestionController.class,
        InterviewAnswerController.class,
        InterviewEventController.class

})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MockInterviewApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> handleUnreadable(HttpMessageNotReadableException exception) {
        log.warn("模拟面试请求JSON无法读取，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求JSON格式或字段不合法");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public BaseResponse<?> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("模拟面试路径参数错误，parameter={}", exception.getName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求路径参数格式不合法");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<?> handleInvalidArgument(IllegalArgumentException exception) {
        log.warn("模拟面试参数错误，error={}", exception.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, exception.getMessage());
    }

    @ExceptionHandler(MockInterviewNotFoundException.class)
    public BaseResponse<?> handleNotFound(MockInterviewNotFoundException exception) {
        log.warn("模拟面试不存在，interviewId={}", exception.interviewId());
        return ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "模拟面试不存在或不属于当前用户");
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

    @ExceptionHandler(MockInterviewVersionConflictException.class)
    public BaseResponse<?> handleVersionConflict(MockInterviewVersionConflictException exception) {
        log.warn("模拟面试版本冲突，interviewId={}, expectedVersion={}",
                exception.interviewId(), exception.expectedVersion());
        return ResultUtils.error(ErrorCode.CONFLICT_ERROR, "模拟面试状态已经变化，请刷新后重试");
    }

    @ExceptionHandler(CoachingRunCapacityRejectedException.class)
    public BaseResponse<?> handleCapacityRejected(CoachingRunCapacityRejectedException exception) {
        log.warn("模拟面试异步执行容量已满，ownerId={}", exception.ownerId().value());
        return ResultUtils.error(ErrorCode.TOO_MANY_REQUEST, "当前执行任务过多，请稍后重试");
    }

    @ExceptionHandler({CoachingRunDispatchRejectedException.class, RunExecutionDeadlineExceededException.class})
    public BaseResponse<?> handleExecutionUnavailable(RuntimeException exception) {
        log.warn("模拟面试异步执行暂不可用，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.SERVICE_UNAVAILABLE_ERROR, "模拟面试执行服务暂不可用，请稍后重试");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public BaseResponse<?> handleIntegrityConflict(DataIntegrityViolationException exception) {
        log.warn("模拟面试数据库约束冲突，exceptionType={}", exception.getClass().getSimpleName());
        return ResultUtils.error(ErrorCode.CONFLICT_ERROR, "模拟面试发生并发冲突，请刷新后重试");
    }

    @ExceptionHandler({IllegalStateException.class, DataAccessException.class})
    public BaseResponse<?> handleInternalFailure(RuntimeException exception) {
        log.error("模拟面试处理失败，exceptionType={}, error={}",
                exception.getClass().getSimpleName(), exception.getMessage(), exception);
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "模拟面试暂时无法处理，请稍后重试");
    }

    @ExceptionHandler(CurrentInterviewQuestionUnavailableException.class)
    public BaseResponse<?> handleCurrentQuestionUnavailable(
            CurrentInterviewQuestionUnavailableException exception
    ) {
        log.info("模拟面试当前没有待回答问题，interviewId={}, status={}",
                exception.interviewId(), exception.interviewStatus());
        return ResultUtils.error(
                ErrorCode.CONFLICT_ERROR,
                "当前面试状态为" + exception.interviewStatus() + "，尚无可回答问题"
        );
    }

    @ExceptionHandler(MockInterviewCancellationConflictException.class)
    public BaseResponse<?> handleCancellationConflict(MockInterviewCancellationConflictException exception) {
        log.info("模拟面试取消冲突，interviewId={}, status={}",
                exception.interviewId(), exception.status());
        return ResultUtils.error(ErrorCode.CONFLICT_ERROR,
                "当前面试已经进入" + exception.status() + "状态，不能取消");
    }
}