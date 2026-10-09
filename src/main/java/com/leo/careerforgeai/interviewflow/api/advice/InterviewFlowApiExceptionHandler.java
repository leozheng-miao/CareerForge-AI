package com.leo.careerforgeai.interviewflow.api.advice;

import com.leo.careerforgeai.interviewflow.api.session.InProgressInterviewResponse;
import com.leo.careerforgeai.interviewflow.api.answer.InterviewAnswerController;
import com.leo.careerforgeai.interviewflow.api.session.InterviewSessionController;
import com.leo.careerforgeai.interviewflow.api.session.InterviewTimerController;
import com.leo.careerforgeai.interviewflow.api.query.InterviewQueryController;
import com.leo.careerforgeai.interviewflow.api.report.InterviewReportController;
import com.leo.careerforgeai.interviewflow.api.sse.InterviewFlowEventController;
import com.leo.careerforgeai.interviewflow.application.answer.InterviewAnswerVersionConflictException;
import com.leo.careerforgeai.interviewflow.application.report.InterviewReportNotFoundException;
import com.leo.careerforgeai.interviewflow.application.session.InterviewFlowNotFoundException;
import com.leo.careerforgeai.interviewflow.application.session.InterviewInProgressException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * @program: CareerForge-AI
 * @description: 将新面试会话参数、owner隔离和单活动冲突映射为真实HTTP状态
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {
        InterviewAnswerController.class,
        InterviewSessionController.class,
        InterviewTimerController.class,
        InterviewQueryController.class,
        InterviewReportController.class,
        InterviewFlowEventController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InterviewFlowApiExceptionHandler {

    @ExceptionHandler(InterviewAnswerVersionConflictException.class)
    public ResponseEntity<BaseResponse<AnswerVersionConflictResponse>>
    handleAnswerVersionConflict(
            InterviewAnswerVersionConflictException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new BaseResponse<>(
                        ErrorCode.CONFLICT_ERROR.getCode(),
                        new AnswerVersionConflictResponse(
                                exception.currentVersion()
                        ),
                        exception.getMessage()
                )
        );
    }

    @ExceptionHandler(InterviewInProgressException.class)
    public ResponseEntity<BaseResponse<InProgressInterviewResponse>>
    handleInProgress(InterviewInProgressException exception) {
        InProgressInterviewResponse response =
                InProgressInterviewResponse.from(exception.recovery());
        log.info("面试正式进入冲突，activeInterviewId={}",
                response.interviewId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new BaseResponse<>(
                        ErrorCode.CONFLICT_ERROR.getCode(),
                        response,
                        "你还有一场未完成的面试，请先完成后再开始新的面试"
                )
        );
    }

    @ExceptionHandler(InterviewFlowNotFoundException.class)
    public ResponseEntity<BaseResponse<?>> handleNotFound(
            InterviewFlowNotFoundException exception
    ) {
        log.info("面试不存在或owner不匹配，interviewId={}",
                exception.interviewId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ResultUtils.error(
                        ErrorCode.NOT_FOUND_ERROR,
                        "面试不存在或不属于当前用户"
                )
        );
    }

    @ExceptionHandler(InterviewReportNotFoundException.class)
    public ResponseEntity<BaseResponse<?>> handleReportNotFound(
            InterviewReportNotFoundException exception
    ) {
        log.info("面试报告不存在，interviewId={}", exception.interviewId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ResultUtils.error(
                        ErrorCode.NOT_FOUND_ERROR,
                        "该场面试没有可用报告"
                )
        );
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class,
            BindException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<BaseResponse<?>> handleBadRequest(
            Exception exception
    ) {
        log.info("面试会话请求参数不合法，exceptionType={}",
                exception.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(
                ResultUtils.error(
                        ErrorCode.PARAMS_ERROR,
                        "请求参数不合法"
                )
        );
    }

    @ExceptionHandler({
            IllegalStateException.class,
            DataIntegrityViolationException.class
    })
    public ResponseEntity<BaseResponse<?>> handleStateConflict(
            RuntimeException exception
    ) {
        log.info("面试会话状态冲突，exceptionType={}",
                exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ResultUtils.error(
                        ErrorCode.CONFLICT_ERROR,
                        "面试状态已变化，请刷新后重试"
                )
        );
    }

    public record AnswerVersionConflictResponse(long currentVersion) {

        public AnswerVersionConflictResponse {
            if (currentVersion < 0) {
                throw new IllegalArgumentException(
                        "currentVersion不能小于0"
                );
            }
        }
    }
}
