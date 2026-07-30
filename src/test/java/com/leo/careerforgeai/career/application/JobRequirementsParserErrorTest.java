package com.leo.careerforgeai.career.application;

import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
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
    @DisplayName("将非法岗位JSON分类为结构化输出错误")
    void shouldClassifyInvalidStructuredOutput() {
        ModelGateway gateway = mock(ModelGateway.class);
        when(gateway.chat(any())).thenReturn(
                new ModelResponse("req-1", "deepseek-v4-flash", "{invalid-json}", null));

        JobRequirementsParser parser = new JobRequirementsParser(
                gateway, JsonMapper.builder().build(), mock(Validator.class));

        assertThatThrownBy(() -> parser.parse("Java开发工程师"))
                .isInstanceOfSatisfying(ModelException.class,
                        exception -> assertThat(exception.getErrorType())
                                .isEqualTo(ModelErrorType.STRUCTURED_OUTPUT_INVALID));
    }
}