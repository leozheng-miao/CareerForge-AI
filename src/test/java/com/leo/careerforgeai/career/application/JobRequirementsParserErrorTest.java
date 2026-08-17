package com.leo.careerforgeai.career.application;

import com.leo.careerforgeai.career.application.requirement.JobRequirementsParseException;
import com.leo.careerforgeai.career.application.requirement.JobRequirementsParseResult;
import com.leo.careerforgeai.career.application.requirement.JobRequirementsParser;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-30 17:32
 **/
/** 验证岗位解析模型输出不符合结构约束时的错误分类。 */
class JobRequirementsParserErrorTest {

    @Test
    @DisplayName("结构化解析失败时保留已经产生的模型成本")
    void shouldPreserveObservedCostForInvalidStructuredOutput() {
        ModelGateway gateway = mock(ModelGateway.class);
        ModelUsage usage = new ModelUsage(90, 10, 100);
        when(gateway.chat(any())).thenReturn(
                new ModelResponse("req-1", "deepseek-v4-flash", "{invalid-json}", usage));

        JobRequirementsParser parser = new JobRequirementsParser(
                gateway, JsonMapper.builder().build(), mock(Validator.class));

        assertThatThrownBy(() -> parser.parseDetailed("Java开发工程师"))
                .isInstanceOfSatisfying(JobRequirementsParseException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(ModelErrorType.STRUCTURED_OUTPUT_INVALID);
                    assertThat(exception.getModelUsage()).isEqualTo(usage);
                    assertThat(exception.getModelDurationMs()).isGreaterThanOrEqualTo(0);
                });
    }

    @Test
    @DisplayName("成功解析时保留内部模型Token和耗时")
    void shouldPreserveModelUsageAndDuration() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.chat(any())).thenReturn(new ModelResponse(
                "req-1",
                "deepseek-v4-flash",
                """
                {
                  "jobTitle": "Java开发工程师",
                  "programmingLanguages": ["Java"],
                  "backendAndInfrastructureRequirements": ["Spring Boot"],
                  "agentRequirements": [],
                  "ragRequirements": [],
                  "engineeringRequirements": [],
                  "bonusQualifications": [],
                  "responsibilities": ["开发后端服务"],
                  "interviewTopics": ["Java基础"]
                }
                """,
                new ModelUsage(100, 30, 130)
        ));

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            JobRequirementsParser parser = new JobRequirementsParser(
                    gateway, JsonMapper.builder().build(), validatorFactory.getValidator());

            JobRequirementsParseResult result = parser.parseDetailed("Java开发工程师，要求掌握Java和Spring Boot");

            assertThat(result.requirements().jobTitle()).isEqualTo("Java开发工程师");
            assertThat(result.modelUsage()).isEqualTo(new ModelUsage(100, 30, 130));
            assertThat(result.modelDurationMs()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("成功结构化输出缺少Token时分类为无效模型响应")
    void shouldRejectSuccessfulResponseWithoutUsage() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.chat(any())).thenReturn(new ModelResponse(
                "req-1",
                "deepseek-v4-flash",
                """
                {
                  "jobTitle": "Java开发工程师",
                  "programmingLanguages": [],
                  "backendAndInfrastructureRequirements": [],
                  "agentRequirements": [],
                  "ragRequirements": [],
                  "engineeringRequirements": [],
                  "bonusQualifications": [],
                  "responsibilities": [],
                  "interviewTopics": []
                }
                """,
                null
        ));

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            JobRequirementsParser parser = new JobRequirementsParser(
                    gateway, JsonMapper.builder().build(), validatorFactory.getValidator());

            assertThatThrownBy(() -> parser.parseDetailed("Java开发工程师"))
                    .isInstanceOfSatisfying(ModelException.class,
                            exception -> assertThat(exception.getErrorType())
                                    .isEqualTo(ModelErrorType.INVALID_RESPONSE));
        }
    }

    @Test
    @DisplayName("模型调用失败时保留耗时但不伪造Token")
    void shouldPreserveDurationWithoutFabricatingUsageOnModelFailure() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.chat(any())).thenThrow(
                new ModelException(ModelErrorType.TIMEOUT, "provider timeout"));

        JobRequirementsParser parser = new JobRequirementsParser(
                gateway, JsonMapper.builder().build(), mock(Validator.class));

        assertThatThrownBy(() -> parser.parseDetailed("Java开发工程师"))
                .isInstanceOfSatisfying(JobRequirementsParseException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(ModelErrorType.TIMEOUT);
                    assertThat(exception.getModelUsage()).isNull();
                    assertThat(exception.getModelDurationMs()).isGreaterThanOrEqualTo(0);
                    assertThat(exception.getMessage()).doesNotContain("provider timeout");
                });
    }
}