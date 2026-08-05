package com.leo.careerforgeai.knowledge.api.advice;

import com.leo.careerforgeai.knowledge.application.answer.RagAnswerException;
import com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.retrieval.KnowledgeRetrievalException;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import com.leo.careerforgeai.shared.web.BaseResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeApiExceptionHandlerTest {

    private final KnowledgeApiExceptionHandler handler = new KnowledgeApiExceptionHandler();

    @Test
    void shouldHideElasticsearchFailureDetails() {
        BaseResponse<?> response = handler.handleRetrievalFailure(
                new KnowledgeRetrievalException("Connection refused http://localhost:9200")
        );

        assertThat(response.getCode()).isEqualTo(ErrorCode.OPERATION_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo("知识检索失败，请稍后重试");
        assertThat(response.getMessage()).doesNotContain("localhost");
    }

    @Test
    void shouldHideModelFailureDetails() {
        BaseResponse<?> response = handler.handleModelFailure(
                new ModelException(ModelErrorType.NETWORK_ERROR, "Connection refused http://localhost:11434")
        );

        assertThat(response.getCode()).isEqualTo(ErrorCode.OPERATION_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo("模型服务调用失败，请稍后重试");
        assertThat(response.getMessage()).doesNotContain("localhost");
    }

    @Test
    void shouldDistinguishAnswerGenerationFailureFromInsufficientContext() {
        BaseResponse<?> response = handler.handleAnswerFailure(
                new RagAnswerException("DeepSeek 返回非法 JSON")
        );

        assertThat(response.getCode()).isEqualTo(ErrorCode.OPERATION_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo("回答生成失败，请稍后重试");
        assertThat(response.getMessage()).isNotEqualTo("无法根据当前知识库确认。");
    }

    @Test
    void shouldReturnParameterErrorForInvalidApplicationInput() {
        BaseResponse<?> response = handler.handleInvalidArgument(
                new IllegalArgumentException("query 不能为空")
        );

        assertThat(response.getCode()).isEqualTo(ErrorCode.PARAMS_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo("query 不能为空");
    }
}