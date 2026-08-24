package com.leo.careerforgeai.model.infrastructure.deepseek.toolcalling;

import com.leo.careerforgeai.model.config.ModelProperties;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.toolcalling.AssistantToolCallsMessage;
import com.leo.careerforgeai.model.domain.toolcalling.FinalAnswerResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingModelResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingRequest;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingTextMessage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallsResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolChoiceMode;
import com.leo.careerforgeai.model.domain.toolcalling.ToolDefinition;
import com.leo.careerforgeai.model.domain.toolcalling.ToolResultMessage;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.model.infrastructure.deepseek.toolcalling.dto.DeepSeekToolCallingRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.net.http.HttpHeaders;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * @program: CareerForge-AI
 * @description: 验证DeepSeek Tool Calling请求映射、输出格式、互斥响应映射和失败分类。
 * @author: Miao Zheng
 * @date: 2026-08-07 14:40
 **/
class DeepSeekToolCallingClientTest {

    private static final String VALID_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": {"type": "string"}
              },
              "required": ["query"]
            }
            """;

    private static final String ONE_TOOL_CALL = """
            [{
              "id": "call-1",
              "type": "function",
              "function": {
                "name": "search_career_materials",
                "arguments": "{\\"query\\":\\"Java并发\\"}"
              }
            }]
            """;

    private static final String VALID_USAGE = """
            {
              "prompt_tokens": 100,
              "completion_tokens": 20,
              "total_tokens": 120
            }
            """;

    private final HttpClient httpClient = mock(HttpClient.class);
    private final JsonMapper jsonMapper = spy(JsonMapper.builder().build());
    private final DeepSeekToolCallingClient client = createClient(jsonMapper);
    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");

    @Test
    @DisplayName("发送JSON初始请求并将tool_calls响应映射为ToolCallsResult")
    void shouldMapInitialRequestAndToolCallsResponse() throws Exception {
        stubResponse(200, response("\"\"", ONE_TOOL_CALL, "tool_calls", VALID_USAGE));

        ToolCallingModelResult result = client.call(initialRequest(VALID_SCHEMA, Duration.ofSeconds(7)));

        assertThat(result).isInstanceOfSatisfying(ToolCallsResult.class, toolCallsResult -> {
            assertThat(toolCallsResult.requestId()).isEqualTo("request-1");
            assertThat(toolCallsResult.model()).isEqualTo("deepseek-v4-flash");
            assertThat(toolCallsResult.usage().inputTokens()).isEqualTo(100);
            assertThat(toolCallsResult.usage().outputTokens()).isEqualTo(20);
            assertThat(toolCallsResult.usage().totalTokens()).isEqualTo(120);
            assertThat(toolCallsResult.toolCalls()).containsExactly(
                    new ToolCall(
                            "call-1",
                            "search_career_materials",
                            "{\"query\":\"Java并发\"}"
                    )
            );
        });

        DeepSeekToolCallingRequest providerRequest = capturedProviderRequest();
        assertThat(providerRequest.model()).isEqualTo("deepseek-v4-flash");
        assertThat(providerRequest.toolChoice()).isEqualTo("auto");
        assertThat(providerRequest.thinking().type()).isEqualTo("disabled");
        assertThat(providerRequest.responseFormat().type()).isEqualTo("json_object");
        assertThat(providerRequest.maxTokens()).isEqualTo(512);
        assertThat(providerRequest.stream()).isFalse();

        assertThat(providerRequest.messages()).hasSize(2);
        assertThat(providerRequest.messages().get(0).role()).isEqualTo("system");
        assertThat(providerRequest.messages().get(1).role()).isEqualTo("user");

        assertThat(providerRequest.tools()).singleElement().satisfies(tool -> {
            assertThat(tool.type()).isEqualTo("function");
            assertThat(tool.function().name()).isEqualTo("search_career_materials");
            assertThat(tool.function().strict()).isFalse();
            assertThat(tool.function().parameters().isObject()).isTrue();
            assertThat(tool.function().parameters().get("type").asText()).isEqualTo("object");
        });

        ArgumentCaptor<HttpRequest> httpRequestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(
                httpRequestCaptor.capture(),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()
        );

        HttpRequest httpRequest = httpRequestCaptor.getValue();
        assertThat(httpRequest.uri()).isEqualTo(URI.create("http://provider.test/chat/completions"));
        assertThat(httpRequest.timeout()).contains(Duration.ofSeconds(7));
        assertThat(httpRequest.headers().firstValue("Authorization")).contains("Bearer test-api-key");
    }

    @Test
    @DisplayName("回放Assistant Tool Calls和Tool Result并映射文本最终回答")
    void shouldReplayToolExchangeAndMapFinalAnswer() throws Exception {
        stubResponse(200, response(
                "\"根据证据，Atomic适合单变量原子更新。\"",
                "null",
                "stop",
                VALID_USAGE
        ));

        ToolCall toolCall = new ToolCall(
                "call-1",
                "search_career_materials",
                "{\"query\":\"Java并发\"}"
        );

        ToolCallingRequest request = new ToolCallingRequest(
                List.of(
                        new ToolCallingTextMessage(ModelRole.SYSTEM, "你是职业辅导助手"),
                        new ToolCallingTextMessage(ModelRole.USER, "查找Java并发面经"),
                        new AssistantToolCallsMessage(List.of(toolCall)),
                        new ToolResultMessage(
                                "call-1",
                                "search_career_materials",
                                "{\"status\":\"SUCCESS\",\"evidence\":[]}"
                        )
                ),
                List.of(toolDefinition(VALID_SCHEMA)),
                ToolChoiceMode.AUTO,
                ModelOutputFormat.TEXT,
                512
        );

        ToolCallingModelResult result = client.call(request);

        assertThat(result).isInstanceOfSatisfying(FinalAnswerResult.class, finalAnswer -> {
            assertThat(finalAnswer.content()).isEqualTo("根据证据，Atomic适合单变量原子更新。");
            assertThat(finalAnswer.usage().totalTokens()).isEqualTo(120);
        });

        DeepSeekToolCallingRequest providerRequest = capturedProviderRequest();
        assertThat(providerRequest.responseFormat().type()).isEqualTo("text");
        assertThat(providerRequest.messages()).hasSize(4);

        DeepSeekToolCallingRequest.Message assistantMessage = providerRequest.messages().get(2);
        assertThat(assistantMessage.role()).isEqualTo("assistant");
        assertThat(assistantMessage.content()).isEmpty();
        assertThat(assistantMessage.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.id()).isEqualTo("call-1");
            assertThat(call.type()).isEqualTo("function");
            assertThat(call.function().name()).isEqualTo("search_career_materials");
            assertThat(call.function().arguments()).isEqualTo("{\"query\":\"Java并发\"}");
        });

        DeepSeekToolCallingRequest.Message toolMessage = providerRequest.messages().get(3);
        assertThat(toolMessage.role()).isEqualTo("tool");
        assertThat(toolMessage.toolCallId()).isEqualTo("call-1");
        assertThat(toolMessage.content()).isEqualTo("{\"status\":\"SUCCESS\",\"evidence\":[]}");
        assertThat(toolMessage.toolCalls()).isNull();
    }

    @Test
    @DisplayName("在HTTP调用前拒绝非法或非对象工具Schema")
    void shouldRejectInvalidToolSchemaBeforeHttpCall() {
        assertConfigurationError(() -> client.call(initialRequest("{invalid-json")));
        assertConfigurationError(() -> client.call(initialRequest("[]")));

        verifyNoInteractions(httpClient);
    }

    @Test
    @DisplayName("将请求序列化失败分类为配置错误")
    void shouldClassifySerializationFailure() throws Exception {
        var validSchemaNode = jsonMapper.readTree(VALID_SCHEMA);
        JacksonException serializationFailure = mock(JacksonException.class);

        JsonMapper brokenMapper = mock(JsonMapper.class);
        when(brokenMapper.readTree(VALID_SCHEMA)).thenReturn(validSchemaNode);
        when(brokenMapper.writeValueAsString(any())).thenThrow(serializationFailure);

        DeepSeekToolCallingClient brokenClient = createClient(brokenMapper);

        assertConfigurationError(() -> brokenClient.call(initialRequest(VALID_SCHEMA)));
        verifyNoInteractions(httpClient);
    }

    @ParameterizedTest
    @CsvSource({
            "400, PROVIDER_REQUEST_REJECTED",
            "401, AUTHENTICATION_ERROR",
            "403, PERMISSION_ERROR",
            "404, MODEL_NOT_FOUND",
            "408, TIMEOUT",
            "429, RATE_LIMITED",
            "500, PROVIDER_ERROR"
    })
    @DisplayName("将HTTP状态码映射为统一模型错误")
    void shouldMapHttpStatus(int statusCode, ModelErrorType expectedType) throws Exception {
        stubResponse(statusCode, "");

        assertModelError(
                () -> client.call(initialRequest(VALID_SCHEMA)),
                expectedType
        );
    }

    @ParameterizedTest
    @MethodSource("transportFailures")
    @DisplayName("区分连接超时、响应超时和普通网络错误")
    void shouldClassifyTransportFailure(
            IOException failure,
            ModelErrorType expectedType,
            String expectedMessage
    ) throws Exception {
        stubSendFailure(failure);

        assertThatThrownBy(() -> client.call(initialRequest(VALID_SCHEMA)))
                .isInstanceOfSatisfying(ModelException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(expectedType);
                    assertThat(exception.getMessage()).isEqualTo(expectedMessage);
                });
    }

    @Test
    @DisplayName("中断调用时恢复线程中断标记")
    void shouldRestoreInterruptedFlag() throws Exception {
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()
        )).thenThrow(new InterruptedException("interrupted"));

        try {
            assertThatThrownBy(() -> client.call(initialRequest(VALID_SCHEMA)))
                    .isInstanceOfSatisfying(ModelException.class, exception ->
                            assertThat(exception.getErrorType()).isEqualTo(ModelErrorType.NETWORK_ERROR));

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest(name = "[{index}] 拒绝非法供应商响应")
    @MethodSource("invalidProviderResponses")
    @DisplayName("将无法安全归类的供应商响应拒绝为INVALID_RESPONSE")
    void shouldRejectInvalidProviderResponse(String responseBody) throws Exception {
        stubResponse(200, responseBody);

        assertModelError(
                () -> client.call(initialRequest(VALID_SCHEMA)),
                ModelErrorType.INVALID_RESPONSE
        );
    }

    @Test
    @DisplayName("解析429响应中的Retry-After秒数、HTTP日期并忽略非法值")
    void shouldParseRetryAfterHeader() throws Exception {
        assertRetryAfter("7", Duration.ofSeconds(7));
        assertRetryAfter("Mon, 24 Aug 2026 00:00:09 GMT", Duration.ofSeconds(9));
        assertRetryAfter("invalid", null);
    }

    private void assertRetryAfter(String header, Duration expectedRetryAfter) throws Exception {
        stubResponse(429, "", Map.of("Retry-After", List.of(header)));

        assertThatThrownBy(() -> client.call(initialRequest(VALID_SCHEMA)))
                .isInstanceOfSatisfying(ModelException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(ModelErrorType.RATE_LIMITED);
                    assertThat(exception.getRetryAfter()).isEqualTo(expectedRetryAfter);
                });
    }

    private static Stream<Object[]> transportFailures() {
        return Stream.of(
                new Object[]{
                        new HttpConnectTimeoutException("connect timeout"),
                        ModelErrorType.TIMEOUT,
                        "连接模型供应商超时"
                },
                new Object[]{
                        new HttpTimeoutException("response timeout"),
                        ModelErrorType.TIMEOUT,
                        "等待模型供应商响应超时"
                },
                new Object[]{
                        new IOException("connection reset"),
                        ModelErrorType.NETWORK_ERROR,
                        "Tool Calling 模型网络调用失败"
                }
        );
    }

    private static Stream<String> invalidProviderResponses() {
        String duplicateCalls = """
                [
                  {
                    "id": "call-1",
                    "type": "function",
                    "function": {
                      "name": "search_career_materials",
                      "arguments": "{}"
                    }
                  },
                  {
                    "id": "call-1",
                    "type": "function",
                    "function": {
                      "name": "search_career_materials",
                      "arguments": "{}"
                    }
                  }
                ]
                """;

        String mismatchedUsage = """
                {
                  "prompt_tokens": 100,
                  "completion_tokens": 20,
                  "total_tokens": 999
                }
                """;

        String oversizedToolCall = """
                [{
                  "id":"call-oversized",
                  "type":"function",
                  "function":{
                    "name":"parse_job_requirements",
                    "arguments":"%s"
                  }
                }]
                """.formatted("a".repeat(30_001));

        return Stream.of(
                "{invalid-json",
                response("\"\"", "null", "stop", VALID_USAGE),
                response("\"最终回答\"", ONE_TOOL_CALL, "stop", VALID_USAGE),
                response("\"\"", "null", "tool_calls", VALID_USAGE),
                response("\"不应同时出现的内容\"", ONE_TOOL_CALL, "tool_calls", VALID_USAGE),
                response("\"\"", duplicateCalls, "tool_calls", VALID_USAGE),
                response("\"未完成\"", "null", "length", VALID_USAGE),
                response("\"最终回答\"", "null", "stop", mismatchedUsage),
                response("\"最终回答\"", "null", "stop", "null"),
                response("\"\"", oversizedToolCall, "tool_calls", VALID_USAGE)
        );
    }

    private static String response(
            String contentJson,
            String toolCallsJson,
            String finishReason,
            String usageJson
    ) {
        return """
                {
                  "id": "request-1",
                  "model": "deepseek-v4-flash",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": %s,
                        "tool_calls": %s
                      },
                      "finish_reason": "%s"
                    }
                  ],
                  "usage": %s
                }
                """.formatted(contentJson, toolCallsJson, finishReason, usageJson);
    }

    private DeepSeekToolCallingRequest capturedProviderRequest() throws Exception {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(jsonMapper).writeValueAsString(captor.capture());
        return (DeepSeekToolCallingRequest) captor.getValue();
    }

    /** 创建使用默认模型超时的JSON初始Tool Calling请求。 */
    private ToolCallingRequest initialRequest(String schema) {
        return initialRequest(schema, Duration.ofSeconds(60));
    }

    /** 创建使用指定模型超时的JSON初始Tool Calling请求。 */
    private ToolCallingRequest initialRequest(String schema, Duration timeout) {
        return new ToolCallingRequest(
                List.of(
                        new ToolCallingTextMessage(ModelRole.SYSTEM, "你是职业辅导助手"),
                        new ToolCallingTextMessage(ModelRole.USER, "查找Java并发面经")
                ),
                List.of(toolDefinition(schema)),
                ToolChoiceMode.AUTO,
                ModelOutputFormat.JSON_OBJECT,
                512,
                timeout
        );
    }

    /** 创建测试使用的搜索工具定义。 */
    private ToolDefinition toolDefinition(String schema) {
        return new ToolDefinition(
                "search_career_materials",
                "检索受控职业知识库证据",
                schema
        );
    }

    /** 创建使用指定JsonMapper和共享HTTP Stub的被测客户端。 */
    private DeepSeekToolCallingClient createClient(JsonMapper mapper) {
        ModelProperties properties = new ModelProperties(
                URI.create("http://provider.test"),
                "test-api-key",
                "deepseek-v4-flash"
        );
        return new DeepSeekToolCallingClient(
                properties,
                mapper,
                httpClient,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
    /** Stub指定HTTP状态和响应正文。 */
    private void stubResponse(int statusCode, String body) throws Exception {
        stubResponse(statusCode, body, Map.of());
    }

    private void stubResponse(int statusCode, String body, Map<String, List<String>> headers) throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()
        )).thenReturn(response);
    }
    /** Stub模型传输层异常。 */
    private void stubSendFailure(IOException failure) throws Exception {
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()
        )).thenThrow(failure);
    }

    /** 断言调用失败被分类为配置错误。 */
    private void assertConfigurationError(ThrowingCall call) {
        assertModelError(call, ModelErrorType.CONFIGURATION_ERROR);
    }

    /** 断言调用失败被映射为指定模型错误。 */
    private void assertModelError(ThrowingCall call, ModelErrorType expectedType) {
        assertThatThrownBy(call::invoke)
                .isInstanceOfSatisfying(ModelException.class,
                        exception -> assertThat(exception.getErrorType()).isEqualTo(expectedType));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void invoke();
    }
}