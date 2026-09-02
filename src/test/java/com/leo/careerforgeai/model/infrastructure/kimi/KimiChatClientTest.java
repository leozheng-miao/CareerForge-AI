package com.leo.careerforgeai.model.infrastructure.kimi;

import com.leo.careerforgeai.model.config.ModelRoutingProperties;
import com.leo.careerforgeai.model.domain.ModelMessage;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.routing.ModelCapability;
import com.leo.careerforgeai.model.domain.routing.ModelExecutionProfile;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import com.leo.careerforgeai.model.domain.routing.ReasoningMode;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Kimi Adapter的请求映射、统一响应和错误分类。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
class KimiChatClientTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void shouldMapRequestAndResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"id":"kimi-request-1","object":"chat.completion","model":"kimi-k2.6",
                    "choices":[{"index":0,"message":{"role":"assistant","content":"{\\"status\\":\\"ok\\"}"},
                    "finish_reason":"stop"}],
                    "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                    """);
        });
        try {
            KimiChatClient client = client(server);
            ModelResponse response = client.chat(profile(), request());

            JsonNode sent = jsonMapper.readTree(requestBody.get());
            assertThat(sent.path("model").asText()).isEqualTo("kimi-k2.6");
            assertThat(sent.path("thinking").path("type").asText()).isEqualTo("disabled");
            assertThat(sent.path("response_format").path("type").asText())
                    .isEqualTo("json_object");
            assertThat(response.model()).isEqualTo("kimi-k2.6");
            assertThat(response.usage().totalTokens()).isEqualTo(15);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapRateLimitWithoutExposingResponseBody() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 429,
                "{\"error\":{\"message\":\"sensitive-provider-message\"}}"));
        try {
            assertThatThrownBy(() -> client(server).chat(profile(), request()))
                    .isInstanceOfSatisfying(ModelException.class, exception -> {
                        assertThat(exception.getErrorType()).isEqualTo(ModelErrorType.RATE_LIMITED);
                        assertThat(exception.getMessage()).doesNotContain("sensitive-provider-message");
                    });
        } finally {
            server.stop(0);
        }
    }

    private KimiChatClient client(HttpServer server) {
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        ModelRoutingProperties properties = new ModelRoutingProperties(
                "routing-v1",
                Map.of("kimi", new ModelRoutingProperties.Provider(baseUrl, "test-key", true)),
                Map.of("kimi-standard", new ModelRoutingProperties.Profile(
                        "kimi", "kimi-k2.6",
                        Set.of(ModelCapability.CHAT, ModelCapability.JSON_OBJECT),
                        ReasoningMode.DISABLED, null, 4_000,
                        Duration.ofSeconds(30), "kimi-test", true)),
                Map.of(ModelTaskType.MEMORY_EXTRACTION, List.of("kimi-standard")));
        return new KimiChatClient(properties, jsonMapper, HttpClient.newHttpClient());
    }

    private ModelExecutionProfile profile() {
        return new ModelExecutionProfile("kimi-standard", "kimi", "kimi-k2.6",
                Set.of(ModelCapability.CHAT, ModelCapability.JSON_OBJECT),
                ReasoningMode.DISABLED, null, 4_000,
                Duration.ofSeconds(30), "kimi-test", true);
    }

    private ModelRequest request() {
        return new ModelRequest(List.of(new ModelMessage(ModelRole.USER, "返回JSON")),
                ModelOutputFormat.JSON_OBJECT, 500, 0.2, Duration.ofSeconds(10));
    }

    private HttpServer server(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> handler.handle(exchange));
        server.start();
        return server;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}