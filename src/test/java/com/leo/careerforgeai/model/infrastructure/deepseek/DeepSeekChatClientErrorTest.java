package com.leo.careerforgeai.model.infrastructure.deepseek;

import com.leo.careerforgeai.model.config.ModelProperties;
import com.leo.careerforgeai.model.domain.ModelMessage;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.routing.ModelCapability;
import com.leo.careerforgeai.model.domain.routing.ModelExecutionProfile;
import com.leo.careerforgeai.model.domain.routing.ReasoningMode;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.model.exception.completion.ModelCompletionException;
import com.leo.careerforgeai.model.exception.completion.ModelCompletionStatus;
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
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
            "400, PROVIDER_REQUEST_REJECTED",
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

    @Test
    @DisplayName("响应头已到达但响应体超过Deadline时仍终止调用")
    void shouldTimeoutWhileReadingResponseBody() throws Exception {
        byte[] responseBody = """
            {
              "id": "request-local-timeout",
              "model": "deepseek-v4-flash",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "最终回答"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 1,
                "completion_tokens": 1,
                "total_tokens": 2
              }
            }
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBody.length);

                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }

                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(responseBody);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            ModelProperties properties = new ModelProperties(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "test-api-key",
                    "deepseek-v4-flash"
            );
            DeepSeekChatClient localClient = new DeepSeekChatClient(
                    properties,
                    JsonMapper.builder().build(),
                    new DeepSeekSseParser(JsonMapper.builder().build()),
                    HttpClient.newHttpClient()
            );
            ModelRequest request = new ModelRequest(
                    List.of(new ModelMessage(ModelRole.USER, "测试消息")),
                    ModelOutputFormat.TEXT,
                    Duration.ofMillis(200)
            );

            assertThatThrownBy(() -> localClient.chat(request))
                    .isInstanceOfSatisfying(ModelException.class, exception ->
                            assertThat(exception.getErrorType()).isEqualTo(ModelErrorType.TIMEOUT));
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "length, OUTPUT_TOKEN_LIMIT_REACHED, PROVIDER_INCOMPLETE",
            "content_filter, CONTENT_FILTERED, PROVIDER_INCOMPLETE",
            "tool_calls, TOOL_CALLS_REQUESTED, PROVIDER_INCOMPLETE",
            "insufficient_system_resource, PROVIDER_RESOURCE_INTERRUPTED, PROVIDER_UNAVAILABLE",
            "unexpected_reason, UNKNOWN_INCOMPLETE, PROVIDER_INCOMPLETE"
    })
    @DisplayName("将DeepSeek非正常完成原因映射为稳定完成状态")
    void shouldClassifyIncompleteFinishReason(
            String finishReason,
            ModelCompletionStatus expectedStatus,
            ModelErrorType expectedErrorType
    ) {
        stubStringResponse(200, providerResponse(finishReason));

        assertThatThrownBy(() ->
                createClient(jsonMapper).chat(createRequest())
        ).isInstanceOfSatisfying(
                ModelCompletionException.class,
                exception -> {
                    assertThat(exception.getErrorType())
                            .isEqualTo(expectedErrorType);
                    assertThat(exception.completionStatus())
                            .isEqualTo(expectedStatus);
                    assertThat(exception.providerFinishReason())
                            .isEqualTo(finishReason);
                    assertThat(exception.providerRequestId())
                            .isEqualTo("provider-request-1");
                    assertThat(exception.model())
                            .isEqualTo("deepseek-v4-flash");
                    assertThat(exception.usage())
                            .isEqualTo(new ModelUsage(10, 6, 16));
                    assertThat(exception.outputChars())
                            .isEqualTo("部分敏感输出".length());
                    assertThat(exception.outputSha256())
                            .matches("[0-9a-f]{64}");
                    assertThat(exception.durationMs()).isNotNegative();
                    assertThat(exception.getMessage())
                            .doesNotContain("部分敏感输出");
                }
        );
    }

    @Test
    @DisplayName("finishReason为stop时返回正常模型响应")
    void shouldReturnCompletedResponse() {
        stubStringResponse(200, providerResponse("stop"));

        ModelResponse response =
                createClient(jsonMapper).chat(createRequest());

        assertThat(response.requestId())
                .isEqualTo("provider-request-1");
        assertThat(response.content())
                .isEqualTo("部分敏感输出");
        assertThat(response.usage())
                .isEqualTo(new ModelUsage(10, 6, 16));
    }

    @Test
    void shouldRejectProfileOwnedByAnotherProvider() {
        ModelExecutionProfile profile = new ModelExecutionProfile(
                "kimi-standard", "kimi", "kimi-k2.6",
                Set.of(ModelCapability.CHAT), ReasoningMode.DISABLED,
                null, 2_000, Duration.ofSeconds(30),
                "kimi-2026-09-02", true);

        assertThatThrownBy(() -> createClient(jsonMapper).chat(profile, createRequest()))
                .isInstanceOfSatisfying(ModelException.class,
                        exception -> assertThat(exception.getErrorType())
                                .isEqualTo(ModelErrorType.CONFIGURATION_ERROR));
    }

    /** 模拟普通非流式HTTP响应。 */
    private void stubStringResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(httpClient.sendAsync(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()
        )).thenReturn(CompletableFuture.completedFuture(response));
    }

    /** 模拟HTTP客户端异步调用失败。 */
    private void stubSendFailure(IOException exception) {
        CompletableFuture<HttpResponse<String>> responseFuture = new CompletableFuture<>();
        responseFuture.completeExceptionally(exception);
        when(httpClient.sendAsync(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()
        )).thenReturn(responseFuture);
    }

    /** 创建使用可控HTTP客户端的DeepSeek适配器。 */
    private DeepSeekChatClient createClient(JsonMapper mapper) {
        ModelProperties properties = new ModelProperties(
                URI.create("http://provider.test"), "test-api-key", "deepseek-v4-flash");

        return new DeepSeekChatClient(
                properties, mapper, new DeepSeekSseParser(mapper), httpClient);
    }

    /** 创建带指定完成原因的脱敏供应商响应。 */
    private String providerResponse(String finishReason) {
        return """
        {
          "id":"provider-request-1",
          "model":"deepseek-v4-flash",
          "choices":[{
            "index":0,
            "message":{
              "role":"assistant",
              "content":"部分敏感输出"
            },
            "finish_reason":"%s"
          }],
          "usage":{
            "prompt_tokens":10,
            "completion_tokens":6,
            "total_tokens":16
          }
        }
        """.formatted(finishReason);
    }

    /** 创建最小合法模型请求。 */
    private ModelRequest createRequest() {
        return new ModelRequest(
                List.of(new ModelMessage(ModelRole.USER, "测试消息")),
                ModelOutputFormat.TEXT);
    }
}