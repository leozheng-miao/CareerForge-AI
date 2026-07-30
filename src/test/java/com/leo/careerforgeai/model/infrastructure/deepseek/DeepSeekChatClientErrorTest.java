package com.leo.careerforgeai.model.infrastructure.deepseek;

import com.leo.careerforgeai.model.config.ModelProperties;
import com.leo.careerforgeai.model.domain.ModelMessage;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.model.infrastructure.deepseek.stream.DeepSeekSseParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证 DeepSeek HTTP、超时、网络和响应解析错误的统一分类。 */
class DeepSeekChatClientErrorTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @ParameterizedTest
    @CsvSource({
            "401, AUTHENTICATION_ERROR",
            "403, PERMISSION_ERROR",
            "404, MODEL_NOT_FOUND",
            "408, TIMEOUT",
            "429, RATE_LIMITED",
            "500, PROVIDER_ERROR"
    })
    @DisplayName("将供应商HTTP状态码映射为统一模型错误")
    void shouldMapHttpStatus(int statusCode, ModelErrorType expectedType) throws Exception {
        stubStringResponse(statusCode, "");

        assertThatThrownBy(() -> createClient(jsonMapper).chat(createRequest()))
                .isInstanceOfSatisfying(ModelException.class,
                        exception -> assertThat(exception.getErrorType()).isEqualTo(expectedType));
    }

    @Test
    @DisplayName("区分模型供应商连接超时")
    void shouldClassifyConnectTimeout() throws Exception {
        stubSendFailure(new HttpConnectTimeoutException("connect timeout"));

        assertThatThrownBy(() -> createClient(jsonMapper).chat(createRequest()))
                .isInstanceOfSatisfying(ModelException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(ModelErrorType.TIMEOUT);
                    assertThat(exception.getMessage()).isEqualTo("连接模型供应商超时");
                });
    }

    @Test
    @DisplayName("区分等待模型响应超时")
    void shouldClassifyResponseTimeout() throws Exception {
        stubSendFailure(new HttpTimeoutException("response timeout"));

        assertThatThrownBy(() -> createClient(jsonMapper).chat(createRequest()))
                .isInstanceOfSatisfying(ModelException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(ModelErrorType.TIMEOUT);
                    assertThat(exception.getMessage()).isEqualTo("等待模型供应商响应超时");
                });
    }

    @Test
    @DisplayName("将普通IO异常分类为网络错误")
    void shouldClassifyNetworkFailure() throws Exception {
        stubSendFailure(new IOException("connection reset"));

        assertThatThrownBy(() -> createClient(jsonMapper).chat(createRequest()))
                .isInstanceOfSatisfying(ModelException.class,
                        exception -> assertThat(exception.getErrorType())
                                .isEqualTo(ModelErrorType.NETWORK_ERROR));
    }

    @Test
    @DisplayName("将请求序列化失败分类为配置错误")
    void shouldClassifySerializationFailure() throws Exception {
        JsonMapper brokenMapper = mock(JsonMapper.class);
        when(brokenMapper.writeValueAsString(any())).thenThrow(mock(JacksonException.class));

        assertThatThrownBy(() -> createClient(brokenMapper).chat(createRequest()))
                .isInstanceOfSatisfying(ModelException.class,
                        exception -> assertThat(exception.getErrorType())
                                .isEqualTo(ModelErrorType.CONFIGURATION_ERROR));
    }

    @Test
    @DisplayName("将非法供应商JSON分类为无效响应")
    void shouldClassifyInvalidProviderResponse() throws Exception {
        stubStringResponse(200, "{invalid-json}");

        assertThatThrownBy(() -> createClient(jsonMapper).chat(createRequest()))
                .isInstanceOfSatisfying(ModelException.class,
                        exception -> assertThat(exception.getErrorType())
                                .isEqualTo(ModelErrorType.INVALID_RESPONSE));
    }

    /** 模拟普通非流式HTTP响应。 */
    private void stubStringResponse(int statusCode, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
    }

    /** 模拟HTTP客户端在发送请求时抛出异常。 */
    private void stubSendFailure(IOException exception) throws Exception {
        when(httpClient.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(exception);
    }

    /** 创建使用可控HTTP客户端的DeepSeek适配器。 */
    private DeepSeekChatClient createClient(JsonMapper mapper) {
        ModelProperties properties = new ModelProperties(
                URI.create("http://provider.test"), "test-api-key", "deepseek-v4-flash");

        return new DeepSeekChatClient(
                properties, mapper, new DeepSeekSseParser(mapper), httpClient);
    }

    /** 创建最小合法模型请求。 */
    private ModelRequest createRequest() {
        return new ModelRequest(
                List.of(new ModelMessage(ModelRole.USER, "测试消息")),
                ModelOutputFormat.TEXT);
    }
}