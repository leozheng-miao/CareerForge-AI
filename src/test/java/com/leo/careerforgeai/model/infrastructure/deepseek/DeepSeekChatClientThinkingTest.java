package com.leo.careerforgeai.model.infrastructure.deepseek;

import com.leo.careerforgeai.model.config.ModelProperties;
import com.leo.careerforgeai.model.domain.*;
import com.leo.careerforgeai.model.domain.routing.*;
import com.leo.careerforgeai.model.infrastructure.deepseek.stream.DeepSeekSseParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证DeepSeek Adapter对Thinking开关、推理强度和思维链隔离的协议映射。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
class DeepSeekChatClientThinkingTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @ParameterizedTest
    @CsvSource({
            "LOW, low",
            "MEDIUM, high",
            "HIGH, high",
            "MAX, max"
    })
    void shouldMapEnabledThinkingAndReasoningEffort(
            ReasoningEffort effort,
            String expectedProviderEffort
    ) throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = startServer(capturedBody);

        try {
            ModelResponse response = client(server).chat(
                    profile(ReasoningMode.ENABLED, effort),
                    request()
            );

            JsonNode sent = jsonMapper.readTree(capturedBody.get());
            assertThat(sent.path("model").asText())
                    .isEqualTo("deepseek-v4-flash");
            assertThat(sent.path("thinking").path("type").asText())
                    .isEqualTo("enabled");
            assertThat(sent.path("reasoning_effort").asText())
                    .isEqualTo(expectedProviderEffort);
            assertThat(sent.has("temperature")).isFalse();
            assertThat(sent.path("stream").asBoolean()).isFalse();

            assertThat(response.requestId()).isEqualTo("deepseek-request-1");
            assertThat(response.model()).isEqualTo("deepseek-v4-flash");
            assertThat(response.content()).isEqualTo("最终答案");
            assertThat(response.content()).doesNotContain("内部推理内容");
            assertThat(response.usage()).isEqualTo(
                    new ModelUsage(10, 8, 18)
            );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepTemperatureAndOmitEffortWhenThinkingDisabled()
            throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = startServer(capturedBody);

        try {
            client(server).chat(
                    profile(ReasoningMode.DISABLED, null),
                    request()
            );

            JsonNode sent = jsonMapper.readTree(capturedBody.get());
            assertThat(sent.path("thinking").path("type").asText())
                    .isEqualTo("disabled");
            assertThat(sent.has("reasoning_effort")).isFalse();
            assertThat(sent.path("temperature").asDouble())
                    .isEqualTo(0.2);
        } finally {
            server.stop(0);
        }
    }

    private DeepSeekChatClient client(HttpServer server) {
        URI baseUrl = URI.create(
                "http://127.0.0.1:" + server.getAddress().getPort()
        );
        ModelProperties properties = new ModelProperties(
                baseUrl,
                "test-api-key",
                "deepseek-v4-flash"
        );
        return new DeepSeekChatClient(
                properties,
                jsonMapper,
                new DeepSeekSseParser(jsonMapper),
                HttpClient.newHttpClient()
        );
    }

    private ModelExecutionProfile profile(
            ReasoningMode reasoningMode,
            ReasoningEffort reasoningEffort
    ) {
        Set<ModelCapability> capabilities =
                reasoningMode == ReasoningMode.ENABLED
                        ? Set.of(
                                ModelCapability.CHAT,
                                ModelCapability.THINKING
                        )
                        : Set.of(ModelCapability.CHAT);

        return new ModelExecutionProfile(
                reasoningMode == ReasoningMode.ENABLED
                        ? "deepseek-thinking"
                        : "deepseek-standard",
                "deepseek",
                "deepseek-v4-flash",
                capabilities,
                reasoningMode,
                reasoningEffort,
                4_000,
                Duration.ofSeconds(30),
                "deepseek-test",
                true
        );
    }

    private ModelRequest request() {
        return new ModelRequest(
                List.of(new ModelMessage(
                        ModelRole.USER,
                        "比较9.11和9.8哪个更大"
                )),
                ModelOutputFormat.TEXT,
                512,
                0.2,
                Duration.ofSeconds(10)
        );
    }

    private HttpServer startServer(
            AtomicReference<String> capturedBody
    ) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );
        server.createContext(
                "/chat/completions",
                exchange -> {
                    capturedBody.set(new String(
                            exchange.getRequestBody().readAllBytes(),
                            StandardCharsets.UTF_8
                    ));
                    respond(exchange);
                }
        );
        server.start();
        return server;
    }

    private void respond(HttpExchange exchange) throws IOException {
        byte[] responseBody = """
                {
                  "id": "deepseek-request-1",
                  "object": "chat.completion",
                  "model": "deepseek-v4-flash",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "reasoning_content": "内部推理内容",
                      "content": "最终答案"
                    },
                    "finish_reason": "stop"
                  }],
                  "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 8,
                    "total_tokens": 18
                  }
                }
                """.getBytes(StandardCharsets.UTF_8);

        try {
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/json"
            );
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
        } finally {
            exchange.close();
        }
    }
}