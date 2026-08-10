package com.leo.careerforgeai.agent.infrastructure.springai.coach;

import com.leo.careerforgeai.agent.application.coach.CareerCoachFinalAnswerValidator;
import com.leo.careerforgeai.agent.application.coach.CareerCoachScopeProvider;
import com.leo.careerforgeai.agent.application.tool.ToolRegistry;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.adapter.SpringAiToolCallbackCatalog;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 通过本地假DeepSeek端点捕获Spring AI实际发送的Tool Calling请求。
 * @author: Miao Zheng
 * @date: 2026-08-10 04:10
 **/
@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "careerforge.agent.spring-ai.enabled=false",
        "careerforge.model.base-url=http://localhost",
        "careerforge.model.api-key=test-placeholder",
        "careerforge.model.name=test-model"
})
class SpringAiDeepSeekRequestCaptureTest {

    @Autowired
    private CareerCoachFinalAnswerValidator finalAnswerValidator;

    @Autowired
    private CareerCoachScopeProvider scopeProvider;

    @Autowired
    private SpringAiToolCallbackCatalog callbackCatalog;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private AgentLoopPolicy policy;

    @Autowired
    private Clock agentClock;

    @Autowired
    private JsonMapper jsonMapper;

    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private final AtomicReference<String> capturedAuthorization = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startFakeDeepSeek() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String finalContent =
                "{\"status\":\"ANSWERED\",\"answer\":\"本地协议捕获测试回答。\",\"citedChunkIds\":[]}";
        String responseBody = """
                {
                  "id": "capture-response",
                  "choices": [{
                    "finish_reason": "stop",
                    "index": 0,
                    "message": {
                      "content": %s,
                      "role": "assistant"
                    }
                  }],
                  "created": 1,
                  "model": "test-model",
                  "object": "chat.completion",
                  "system_fingerprint": "capture-test",
                  "usage": {
                    "completion_tokens": 10,
                    "prompt_tokens": 20,
                    "total_tokens": 30
                  }
                }
                """.formatted(jsonMapper.writeValueAsString(finalContent));

        server.createContext("/chat/completions", exchange -> {
            capturedBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            ));
            capturedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));

            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (var outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopFakeDeepSeek() {
        if (server != null) server.stop(0);
    }

    @Test
    @DisplayName("实际DeepSeek请求保留公共工具定义和对照模型参数")
    void shouldSendEquivalentToolDefinitionsToDeepSeekProtocol() throws Exception {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        DeepSeekApi deepSeekApi = DeepSeekApi.builder()
                .baseUrl(baseUrl)
                .apiKey("test-api-key")
                .build();
        DeepSeekChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .options(DeepSeekChatOptions.builder()
                        .model("test-model")
                        .build())
                .build();
        SpringAiCareerCoachService service = new SpringAiCareerCoachService(
                ChatClient.create(chatModel),
                finalAnswerValidator,
                scopeProvider,
                callbackCatalog.callbacks(),
                policy,
                agentClock
        );

        SpringAiCareerCoachResult result = service.coach("请给我一般职业建议。");

        assertThat(result.answer().status()).isEqualTo(CareerCoachAnswerStatus.ANSWERED);
        assertThat(capturedAuthorization.get()).isEqualTo("Bearer test-api-key");

        JsonNode request = jsonMapper.readTree(capturedBody.get());
        assertThat(request.get("model").asText()).isEqualTo("test-model");
        assertThat(request.get("stream").asBoolean()).isFalse();
        assertThat(request.get("max_tokens").asInt())
                .isEqualTo(policy.maxOutputTokensPerModelCall());
        assertThat(request.get("tool_choice").asText()).isEqualTo("auto");
        assertThat(request.get("response_format").get("type").asText())
                .isEqualTo("json_object");
        assertThat(request.get("messages").get(0).get("role").asText())
                .isEqualTo("system");
        assertThat(request.get("messages").get(1).get("role").asText())
                .isEqualTo("user");
        assertThat(request.get("tools").size())
                .isEqualTo(toolRegistry.definitions().size());

        for (var nativeDefinition : toolRegistry.definitions()) {
            JsonNode function = findFunction(request, nativeDefinition.name());

            assertThat(function.get("name").asText())
                    .isEqualTo(nativeDefinition.name());
            assertThat(function.get("description").asText())
                    .isEqualTo(nativeDefinition.description());
            assertThat(function.get("parameters"))
                    .isEqualTo(jsonMapper.readTree(nativeDefinition.inputSchemaJson()));

            // Spring AI 2.0的公共ToolDefinition没有暴露DeepSeek strict字段。
            assertThat(function.get("strict")).isNull();
        }

        // Spring AI 2.0的DeepSeek请求模型没有暴露thinking配置。
        assertThat(request.get("thinking")).isNull();
    }

    private JsonNode findFunction(JsonNode request, String toolName) {
        JsonNode tools = request.get("tools");
        for (int index = 0; index < tools.size(); index++) {
            JsonNode tool = tools.get(index);
            JsonNode function = tool.get("function");
            if (toolName.equals(function.get("name").asText())) return function;
        }
        throw new AssertionError("实际DeepSeek请求缺少工具：" + toolName);
    }
}