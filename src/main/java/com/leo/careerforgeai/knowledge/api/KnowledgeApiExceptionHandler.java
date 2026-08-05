package com.leo.careerforgeai.knowledge.api;

import com.leo.careerforgeai.knowledge.application.RagAnswerException;
import com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.KnowledgeRetrievalException;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        KnowledgeRagController.class,
        KnowledgeRetrievalDebugController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class KnowledgeApiExceptionHandler {

    @ExceptionHandler(KnowledgeRetrievalException.class)
    public BaseResponse<?> handleRetrievalFailure(KnowledgeRetrievalException e) {
        log.error("知识检索接口失败，errorType={}, error={}", e.getClass().getSimpleName(), e.getMessage(), e);
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "知识检索失败，请稍后重试");
    }

    @ExceptionHandler(ModelException.class)
    public BaseResponse<?> handleModelFailure(ModelException e) {
        log.error("知识接口模型调用失败，modelErrorType={}, error={}", e.getErrorType(), e.getMessage(), e);
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "模型服务调用失败，请稍后重试");
    }

    @ExceptionHandler(RagAnswerException.class)
    public BaseResponse<?> handleAnswerFailure(RagAnswerException e) {
        log.error("RAG回答生成失败，errorType={}, error={}", e.getClass().getSimpleName(), e.getMessage(), e);
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "回答生成失败，请稍后重试");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<?> handleInvalidArgument(IllegalArgumentException e) {
        log.warn("知识接口参数错误，error={}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, e.getMessage());
    }
}