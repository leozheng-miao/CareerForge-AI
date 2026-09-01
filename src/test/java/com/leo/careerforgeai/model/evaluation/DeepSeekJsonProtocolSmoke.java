package com.leo.careerforgeai.model.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import tools.jackson.core.JacksonException;

import java.util.LinkedHashMap;

/**
 * @program: CareerForge-AI
 * @description: 直接调用DeepSeek官方API验证JSON、Thinking、Tool Calling、Streaming、完成原因和错误信封
 * @author: Miao Zheng
 * @date: 2026-09-01
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class DeepSeekJsonProtocolSmoke {

    private static final URI ENDPOINT =
            URI.create("https://api.deepseek.com/chat/completions");
    private static final String MODEL =
            System.getenv().getOrDefault("DEEPSEEK_MODEL", "deepseek-v4-flash");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 验证普通JSON调用的模型、完成原因、用量和输出结构。 */
    @Test
    void shouldReturnValidJsonWithObservableProtocolFields() throws Exception {
        String requestBody = jsonMapper.writeValueAsString(Map.of(
                "model", MODEL,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", """
                                        只输出JSON，不得输出Markdown或解释。
                                        目标JSON示例：{"status":"ok"}
                                        """
                        ),
                        Map.of(
                                "role", "user",
                                "content", "请严格返回JSON对象：{\"status\":\"ok\"}"
                        )
                ),
                "response_format", Map.of("type", "json_object"),
                "thinking", Map.of("type", "disabled"),
                "max_tokens", 64,
                "stream", false
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(ENDPOINT)
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + System.getenv("DEEPSEEK_API_KEY"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        long startedNanos = System.nanoTime();
        HttpResponse<String> response = sendWithinDeadline(request);
        long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

        assertThat(response.statusCode())
                .as("httpStatus=%s, responseChars=%s",
                        response.statusCode(), response.body().length())
                .isEqualTo(200);

        JsonNode root = jsonMapper.readTree(response.body());
        assertThat(root.path("object").asText()).isEqualTo("chat.completion");
        assertThat(root.path("id").asText()).isNotBlank();
        assertThat(root.path("model").asText()).isEqualTo(MODEL);
        assertThat(root.path("choices").size()).isEqualTo(1);

        JsonNode choice = root.path("choices").path(0);
        assertThat(choice.path("finish_reason").asText()).isEqualTo("stop");

        JsonNode message = choice.path("message");
        String content = message.path("content").asText();
        assertThat(content).isNotBlank();

        JsonNode reasoningContent = message.path("reasoning_content");
        assertThat(reasoningContent.isMissingNode()
                || reasoningContent.isNull()
                || reasoningContent.asText().isBlank()).isTrue();

        JsonNode output = jsonMapper.readTree(content);
        assertThat(output.path("status").asText()).isEqualTo("ok");

        JsonNode usage = root.path("usage");
        long promptTokens = usage.path("prompt_tokens").asLong();
        long completionTokens = usage.path("completion_tokens").asLong();
        long totalTokens = usage.path("total_tokens").asLong();

        assertThat(promptTokens).isPositive();
        assertThat(completionTokens).isPositive();
        assertThat(totalTokens).isEqualTo(promptTokens + completionTokens);

        System.out.printf(
                Locale.ROOT,
                "provider=DEEPSEEK, httpStatus=%d, providerRequestId=%s, "
                        + "model=%s, finishReason=%s, outputChars=%d, outputHash=%s, "
                        + "promptTokens=%d, completionTokens=%d, totalTokens=%d, durationMs=%d%n",
                response.statusCode(),
                root.path("id").asText(),
                root.path("model").asText(),
                choice.path("finish_reason").asText(),
                content.length(),
                sha256(content),
                promptTokens,
                completionTokens,
                totalTokens,
                durationMs
        );
    }

    /** 验证Thinking内容与最终答案分离，并且推理Token可观测。 */
    @Test
    void shouldExposeReasoningSeparatelyWhenThinkingEnabled() throws Exception {
        String requestBody = jsonMapper.writeValueAsString(Map.of(
                "model", MODEL,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", "比较9.11和9.8哪个更大，只给出最终结论。"
                        )
                ),
                "thinking", Map.of("type", "enabled"),
                "reasoning_effort", "low",
                "max_tokens", 512,
                "stream", false
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(ENDPOINT)
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + System.getenv("DEEPSEEK_API_KEY"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        long startedNanos = System.nanoTime();
        HttpResponse<String> response = sendWithinDeadline(request);
        long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

        assertThat(response.statusCode())
                .as("httpStatus=%s, responseChars=%s",
                        response.statusCode(), response.body().length())
                .isEqualTo(200);

        JsonNode root = jsonMapper.readTree(response.body());
        assertThat(root.path("object").asText()).isEqualTo("chat.completion");
        assertThat(root.path("id").asText()).isNotBlank();
        assertThat(root.path("model").asText()).isEqualTo(MODEL);
        assertThat(root.path("choices").size()).isEqualTo(1);

        JsonNode choice = root.path("choices").path(0);
        assertThat(choice.path("finish_reason").asText()).isEqualTo("stop");

        JsonNode message = choice.path("message");
        String content = message.path("content").asText();
        String reasoningContent = message.path("reasoning_content").asText();

        assertThat(content).isNotBlank();
        assertThat(reasoningContent).isNotBlank();
        assertThat(reasoningContent).isNotEqualTo(content);

        JsonNode usage = root.path("usage");
        long promptTokens = usage.path("prompt_tokens").asLong();
        long completionTokens = usage.path("completion_tokens").asLong();
        long totalTokens = usage.path("total_tokens").asLong();
        long reasoningTokens = usage.path("completion_tokens_details")
                .path("reasoning_tokens")
                .asLong();

        assertThat(promptTokens).isPositive();
        assertThat(completionTokens).isPositive();
        assertThat(reasoningTokens).isPositive();
        assertThat(completionTokens).isGreaterThanOrEqualTo(reasoningTokens);
        assertThat(totalTokens).isEqualTo(promptTokens + completionTokens);

        System.out.printf(
                Locale.ROOT,
                "provider=DEEPSEEK, mode=THINKING, effort=LOW, httpStatus=%d, "
                        + "providerRequestId=%s, model=%s, finishReason=%s, "
                        + "outputChars=%d, outputHash=%s, reasoningChars=%d, "
                        + "promptTokens=%d, completionTokens=%d, reasoningTokens=%d, "
                        + "totalTokens=%d, durationMs=%d%n",
                response.statusCode(),
                root.path("id").asText(),
                root.path("model").asText(),
                choice.path("finish_reason").asText(),
                content.length(),
                sha256(content),
                reasoningContent.length(),
                promptTokens,
                completionTokens,
                reasoningTokens,
                totalTokens,
                durationMs
        );
    }

    /** 验证非Thinking Tool Calling信封、函数选择和参数JSON。 */
    @Test
    void shouldReturnValidatedToolCallWithoutExecutingTool() throws Exception {
        String requestBody = jsonMapper.writeValueAsString(Map.of(
                "model", MODEL,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", "查询Java技能差距，必须调用lookup_skill_gap工具，skill固定为Java。"
                        )
                ),
                "thinking", Map.of("type", "disabled"),
                "tools", List.of(
                        Map.of(
                                "type", "function",
                                "function", Map.of(
                                        "name", "lookup_skill_gap",
                                        "description", "查询指定技能的差距信息",
                                        "parameters", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "skill", Map.of(
                                                                "type", "string",
                                                                "enum", List.of("Java")
                                                        )
                                                ),
                                                "required", List.of("skill"),
                                                "additionalProperties", false
                                        )
                                )
                        )
                ),
                "tool_choice", Map.of(
                        "type", "function",
                        "function", Map.of("name", "lookup_skill_gap")
                ),
                "max_tokens", 128,
                "stream", false
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(ENDPOINT)
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + System.getenv("DEEPSEEK_API_KEY"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        long startedNanos = System.nanoTime();
        HttpResponse<String> response = sendWithinDeadline(request);
        long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

        assertThat(response.statusCode())
                .as("httpStatus=%s, responseChars=%s",
                        response.statusCode(), response.body().length())
                .isEqualTo(200);

        JsonNode root = jsonMapper.readTree(response.body());
        assertThat(root.path("object").asText()).isEqualTo("chat.completion");
        assertThat(root.path("id").asText()).isNotBlank();
        assertThat(root.path("model").asText()).isEqualTo(MODEL);
        assertThat(root.path("choices").size()).isEqualTo(1);

        JsonNode choice = root.path("choices").path(0);
        assertThat(choice.path("finish_reason").asText()).isEqualTo("tool_calls");

        JsonNode message = choice.path("message");
        JsonNode reasoningContent = message.path("reasoning_content");
        assertThat(reasoningContent.isMissingNode()
                || reasoningContent.isNull()
                || reasoningContent.asText().isBlank()).isTrue();

        JsonNode toolCalls = message.path("tool_calls");
        assertThat(toolCalls.isArray()).isTrue();
        assertThat(toolCalls.size()).isEqualTo(1);

        JsonNode toolCall = toolCalls.path(0);
        assertThat(toolCall.path("id").asText()).isNotBlank();
        assertThat(toolCall.path("type").asText()).isEqualTo("function");
        assertThat(toolCall.path("function").path("name").asText())
                .isEqualTo("lookup_skill_gap");

        String argumentsText = toolCall.path("function").path("arguments").asText();
        assertThat(argumentsText).isNotBlank();

        JsonNode arguments = jsonMapper.readTree(argumentsText);
        assertThat(arguments.isObject()).isTrue();
        assertThat(arguments.size()).isEqualTo(1);
        assertThat(arguments.path("skill").asText()).isEqualTo("Java");

        JsonNode usage = root.path("usage");
        long promptTokens = usage.path("prompt_tokens").asLong();
        long completionTokens = usage.path("completion_tokens").asLong();
        long totalTokens = usage.path("total_tokens").asLong();

        assertThat(promptTokens).isPositive();
        assertThat(completionTokens).isPositive();
        assertThat(totalTokens).isEqualTo(promptTokens + completionTokens);

        System.out.printf(
                Locale.ROOT,
                "provider=DEEPSEEK, mode=TOOL_CALLING, thinking=DISABLED, "
                        + "httpStatus=%d, providerRequestId=%s, model=%s, "
                        + "finishReason=%s, toolCallId=%s, functionName=%s, "
                        + "argumentsChars=%d, argumentsHash=%s, "
                        + "promptTokens=%d, completionTokens=%d, totalTokens=%d, durationMs=%d%n",
                response.statusCode(),
                root.path("id").asText(),
                root.path("model").asText(),
                choice.path("finish_reason").asText(),
                toolCall.path("id").asText(),
                toolCall.path("function").path("name").asText(),
                argumentsText.length(),
                sha256(argumentsText),
                promptTokens,
                completionTokens,
                totalTokens,
                durationMs
        );
    }

    /** 验证Streaming SSE事件顺序、终止标记及最终Token用量。 */
    @Test
    void shouldReturnOrderedSseChunksWithTerminalUsage() throws Exception {
        String requestBody = jsonMapper.writeValueAsString(Map.of(
                "model", MODEL,
                "messages", List.of(
                        Map.of("role", "user", "content", "只回复：OK")
                ),
                "thinking", Map.of("type", "disabled"),
                "max_tokens", 32,
                "stream", true,
                "stream_options", Map.of("include_usage", true)
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(ENDPOINT)
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + System.getenv("DEEPSEEK_API_KEY"))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        long startedNanos = System.nanoTime();
        HttpResponse<String> response = sendWithinDeadline(request);
        long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

        assertThat(response.statusCode())
                .as("httpStatus=%s, responseChars=%s",
                        response.statusCode(), response.body().length())
                .isEqualTo(200);

        String contentType = response.headers()
                .firstValue("Content-Type")
                .orElse("");
        assertThat(contentType).startsWith("text/event-stream");

        List<String> payloads = new ArrayList<>();
        response.body().lines()
                .map(String::strip)
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).strip())
                .filter(payload -> !payload.isBlank())
                .forEach(payloads::add);

        assertThat(payloads).isNotEmpty();
        assertThat(payloads.get(payloads.size() - 1)).isEqualTo("[DONE]");

        String providerRequestId = null;
        String responseModel = null;
        String finishReason = null;
        StringBuilder content = new StringBuilder();
        int contentChunkCount = 0;
        long promptTokens = 0;
        long completionTokens = 0;
        long totalTokens = 0;

        for (int index = 0; index < payloads.size() - 1; index++) {
            JsonNode chunk = jsonMapper.readTree(payloads.get(index));

            assertThat(chunk.path("object").asText())
                    .isEqualTo("chat.completion.chunk");
            assertThat(chunk.path("id").asText()).isNotBlank();
            assertThat(chunk.path("model").asText()).isNotBlank();
            assertThat(chunk.path("choices").size()).isEqualTo(1);

            if (providerRequestId == null) {
                providerRequestId = chunk.path("id").asText();
                responseModel = chunk.path("model").asText();
            } else {
                assertThat(chunk.path("id").asText()).isEqualTo(providerRequestId);
                assertThat(chunk.path("model").asText()).isEqualTo(responseModel);
            }

            JsonNode choice = chunk.path("choices").path(0);
            JsonNode delta = choice.path("delta");
            JsonNode reasoningContent = delta.path("reasoning_content");

            assertThat(reasoningContent.isMissingNode()
                    || reasoningContent.isNull()
                    || reasoningContent.asText().isBlank()).isTrue();

            JsonNode contentNode = delta.path("content");
            if (!contentNode.isMissingNode()
                    && !contentNode.isNull()
                    && !contentNode.asText().isEmpty()) {
                content.append(contentNode.asText());
                contentChunkCount++;
            }

            JsonNode finishNode = choice.path("finish_reason");
            if (!finishNode.isMissingNode() && !finishNode.isNull()) {
                assertThat(finishReason)
                        .as("只允许一个Chunk携带finish_reason")
                        .isNull();
                finishReason = finishNode.asText();
            }

            JsonNode usage = chunk.path("usage");
            if (!usage.isMissingNode() && !usage.isNull()) {
                promptTokens = usage.path("prompt_tokens").asLong();
                completionTokens = usage.path("completion_tokens").asLong();
                totalTokens = usage.path("total_tokens").asLong();
            }
        }

        assertThat(providerRequestId).isNotBlank();
        assertThat(responseModel).isEqualTo(MODEL);
        assertThat(contentChunkCount).isPositive();
        assertThat(content).isNotEmpty();
        assertThat(finishReason).isEqualTo("stop");
        assertThat(promptTokens).isPositive();
        assertThat(completionTokens).isPositive();
        assertThat(totalTokens).isEqualTo(promptTokens + completionTokens);

        System.out.printf(
                Locale.ROOT,
                "provider=DEEPSEEK, mode=STREAMING, thinking=DISABLED, "
                        + "httpStatus=%d, providerRequestId=%s, model=%s, "
                        + "finishReason=%s, sseEvents=%d, contentChunks=%d, "
                        + "outputChars=%d, outputHash=%s, promptTokens=%d, "
                        + "completionTokens=%d, totalTokens=%d, durationMs=%d%n",
                response.statusCode(),
                providerRequestId,
                responseModel,
                finishReason,
                payloads.size(),
                contentChunkCount,
                content.length(),
                sha256(content.toString()),
                promptTokens,
                completionTokens,
                totalTokens,
                durationMs
        );
    }

    /** 验证输出预算耗尽时供应商明确返回finish_reason=length。 */
    @Test
    void shouldExposeLengthFinishReasonWhenTokenBudgetExhausted() throws Exception {
        HttpRequest request = createJsonRequest(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of(
                                        "role", "user",
                                        "content", "连续输出从1到100的编号及每项说明，不得提前结束。"
                                )
                        ),
                        "thinking", Map.of("type", "disabled"),
                        "max_tokens", 1,
                        "stream", false
                ),
                System.getenv("DEEPSEEK_API_KEY")
        );

        long startedNanos = System.nanoTime();
        HttpResponse<String> response = sendWithinDeadline(request);
        long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

        assertThat(response.statusCode())
                .as("httpStatus=%s, responseChars=%s",
                        response.statusCode(), response.body().length())
                .isEqualTo(200);

        JsonNode root = jsonMapper.readTree(response.body());
        assertThat(root.path("object").asText()).isEqualTo("chat.completion");
        assertThat(root.path("id").asText()).isNotBlank();
        assertThat(root.path("model").asText()).isEqualTo(MODEL);
        assertThat(root.path("choices").size()).isEqualTo(1);

        JsonNode choice = root.path("choices").path(0);
        assertThat(choice.path("finish_reason").asText()).isEqualTo("length");

        JsonNode contentNode = choice.path("message").path("content");
        String content = contentNode.isMissingNode() || contentNode.isNull()
                ? ""
                : contentNode.asText();

        JsonNode usage = root.path("usage");
        long promptTokens = usage.path("prompt_tokens").asLong();
        long completionTokens = usage.path("completion_tokens").asLong();
        long totalTokens = usage.path("total_tokens").asLong();

        assertThat(promptTokens).isPositive();
        assertThat(completionTokens).isPositive();
        assertThat(totalTokens).isEqualTo(promptTokens + completionTokens);

        System.out.printf(
                Locale.ROOT,
                "provider=DEEPSEEK, mode=TOKEN_BUDGET_EXHAUSTED, "
                        + "httpStatus=%d, providerRequestId=%s, model=%s, "
                        + "finishReason=%s, outputChars=%d, outputHash=%s, "
                        + "promptTokens=%d, completionTokens=%d, totalTokens=%d, durationMs=%d%n",
                response.statusCode(),
                root.path("id").asText(),
                root.path("model").asText(),
                choice.path("finish_reason").asText(),
                content.length(),
                sha256(content),
                promptTokens,
                completionTokens,
                totalTokens,
                durationMs
        );
    }

    /** 验证真实401状态和供应商错误信封，不记录错误正文。 */
    @Test
    void shouldReturnStructuredAuthenticationError() throws Exception {
        HttpRequest request = createJsonRequest(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of("role", "user", "content", "只回复OK")
                        ),
                        "thinking", Map.of("type", "disabled"),
                        "max_tokens", 16,
                        "stream", false
                ),
                "cp0-invalid-api-key"
        );

        long startedNanos = System.nanoTime();
        HttpResponse<String> response = sendWithinDeadline(request);
        long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers()
                .firstValue("Content-Type")
                .orElse("")).contains("application/json");
        assertThat(response.body())
                .doesNotContain(System.getenv("DEEPSEEK_API_KEY"));

        JsonNode root = jsonMapper.readTree(response.body());
        JsonNode error = root.path("error");

        assertThat(error.isObject()).isTrue();
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.path("type").asText()).isNotBlank();

        JsonNode codeNode = error.path("code");
        String errorCode = codeNode.isMissingNode() || codeNode.isNull()
                ? "UNKNOWN"
                : codeNode.asText();
        String providerRequestId = response.headers()
                .firstValue("x-request-id")
                .orElse("UNKNOWN");

        System.out.printf(
                Locale.ROOT,
                "provider=DEEPSEEK, mode=ERROR_PROTOCOL, httpStatus=%d, "
                        + "providerRequestId=%s, errorType=%s, errorCode=%s, "
                        + "bodyChars=%d, bodyHash=%s, durationMs=%d%n",
                response.statusCode(),
                providerRequestId,
                error.path("type").asText(),
                errorCode,
                response.body().length(),
                sha256(response.body()),
                durationMs
        );
    }

    /** 验证Thinking Tool Calling跨轮完整回传reasoning_content。 */
    /** 验证Thinking Tool Calling跨轮完整回传reasoning_content。 */
    @Test
    void shouldRoundTripReasoningContentAcrossThinkingToolCalls() throws Exception {
        Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", """
                    你不能自行判断当前技能等级。
                    必须且只能先调用一次lookup_skill_level查询Java等级。
                    收到工具结果后直接给出最终结论，不得再次调用工具。
                    """
        );
        List<Map<String, Object>> tools = List.of(
                Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", "lookup_skill_level",
                                "description", "查询指定技能的当前等级",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "skill", Map.of(
                                                        "type", "string",
                                                        "enum", List.of("Java")
                                                )
                                        ),
                                        "required", List.of("skill"),
                                        "additionalProperties", false
                                )
                        )
                )
        );

        HttpRequest firstRequest = createJsonRequest(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(userMessage),
                        "thinking", Map.of("type", "enabled"),
                        "reasoning_effort", "low",
                        "tools", tools,
                        "max_tokens", 512,
                        "stream", false
                ),
                System.getenv("DEEPSEEK_API_KEY")
        );

        long startedNanos = System.nanoTime();
        HttpResponse<String> firstResponse = sendWithinDeadline(firstRequest);

        assertThat(firstResponse.statusCode())
                .as(
                        "firstHttpStatus=%s, responseChars=%s",
                        firstResponse.statusCode(),
                        firstResponse.body().length()
                )
                .isEqualTo(200);

        JsonNode firstRoot = jsonMapper.readTree(firstResponse.body());
        assertThat(firstRoot.path("object").asText()).isEqualTo("chat.completion");
        assertThat(firstRoot.path("id").asText()).isNotBlank();
        assertThat(firstRoot.path("model").asText()).isEqualTo(MODEL);
        assertThat(firstRoot.path("choices").size()).isEqualTo(1);

        JsonNode firstChoice = firstRoot.path("choices").path(0);
        assertThat(firstChoice.path("finish_reason").asText())
                .isEqualTo("tool_calls");

        JsonNode firstMessage = firstChoice.path("message");
        String firstReasoningContent =
                firstMessage.path("reasoning_content").asText();
        assertThat(firstReasoningContent).isNotBlank();

        JsonNode firstToolCalls = firstMessage.path("tool_calls");
        assertThat(firstToolCalls.isArray()).isTrue();
        assertThat(firstToolCalls.size()).isEqualTo(1);

        JsonNode firstToolCall = firstToolCalls.path(0);
        String toolCallId = firstToolCall.path("id").asText();
        String functionName = firstToolCall.path("function").path("name").asText();
        String argumentsText =
                firstToolCall.path("function").path("arguments").asText();

        assertThat(toolCallId).isNotBlank();
        assertThat(functionName).isEqualTo("lookup_skill_level");
        assertThat(argumentsText).isNotBlank();

        JsonNode arguments = jsonMapper.readTree(argumentsText);
        assertThat(arguments.isObject()).isTrue();
        assertThat(arguments.size()).isEqualTo(1);
        assertThat(arguments.path("skill").asText()).isEqualTo("Java");

        Map<String, Object> assistantMessage = new LinkedHashMap<>();
        assistantMessage.put("role", "assistant");

        JsonNode firstContentNode = firstMessage.path("content");
        assistantMessage.put(
                "content",
                firstContentNode.isMissingNode() || firstContentNode.isNull()
                        ? ""
                        : firstContentNode.asText()
        );
        assistantMessage.put("reasoning_content", firstReasoningContent);
        assistantMessage.put("tool_calls", firstToolCalls);

        Map<String, Object> toolResultMessage = Map.of(
                "role", "tool",
                "tool_call_id", toolCallId,
                "content", "{\"skill\":\"Java\",\"level\":\"INTERMEDIATE\"}"
        );

        HttpRequest secondRequest = createJsonRequest(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                userMessage,
                                assistantMessage,
                                toolResultMessage
                        ),
                        "thinking", Map.of("type", "enabled"),
                        "reasoning_effort", "low",
                        "tools", tools,
                        "max_tokens", 512,
                        "stream", false
                ),
                System.getenv("DEEPSEEK_API_KEY")
        );

        HttpResponse<String> secondResponse = sendWithinDeadline(secondRequest);
        long durationMs =
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

        assertThat(secondResponse.statusCode())
                .as(
                        "secondHttpStatus=%s, responseChars=%s",
                        secondResponse.statusCode(),
                        secondResponse.body().length()
                )
                .isEqualTo(200);

        JsonNode secondRoot = jsonMapper.readTree(secondResponse.body());
        assertThat(secondRoot.path("object").asText()).isEqualTo("chat.completion");
        assertThat(secondRoot.path("id").asText()).isNotBlank();
        assertThat(secondRoot.path("model").asText()).isEqualTo(MODEL);
        assertThat(secondRoot.path("choices").size()).isEqualTo(1);

        JsonNode secondChoice = secondRoot.path("choices").path(0);
        assertThat(secondChoice.path("finish_reason").asText()).isEqualTo("stop");

        JsonNode secondMessage = secondChoice.path("message");
        String finalContent = secondMessage.path("content").asText();
        String secondReasoningContent =
                secondMessage.path("reasoning_content").asText();

        assertThat(finalContent).isNotBlank();
        // 最终轮允许不再产生新的reasoning_content；首轮内容已完整回传即可。
        assertThat(secondReasoningContent).isNotNull();

        JsonNode secondToolCalls = secondMessage.path("tool_calls");
        assertThat(secondToolCalls.isMissingNode()
                || secondToolCalls.isNull()
                || secondToolCalls.isEmpty()).isTrue();

        JsonNode firstUsage = firstRoot.path("usage");
        long firstPromptTokens = firstUsage.path("prompt_tokens").asLong();
        long firstCompletionTokens = firstUsage.path("completion_tokens").asLong();
        long firstTotalTokens = firstUsage.path("total_tokens").asLong();
        long firstReasoningTokens = firstUsage.path("completion_tokens_details")
                .path("reasoning_tokens")
                .asLong();

        JsonNode secondUsage = secondRoot.path("usage");
        long secondPromptTokens = secondUsage.path("prompt_tokens").asLong();
        long secondCompletionTokens =
                secondUsage.path("completion_tokens").asLong();
        long secondTotalTokens = secondUsage.path("total_tokens").asLong();
        long secondReasoningTokens = secondUsage.path("completion_tokens_details")
                .path("reasoning_tokens")
                .asLong();

        assertThat(firstPromptTokens).isPositive();
        assertThat(firstCompletionTokens).isPositive();
        assertThat(firstReasoningTokens).isPositive();
        assertThat(firstTotalTokens)
                .isEqualTo(firstPromptTokens + firstCompletionTokens);

        assertThat(secondPromptTokens).isPositive();
        assertThat(secondCompletionTokens).isPositive();
        assertThat(secondReasoningTokens).isNotNegative();
        assertThat(secondCompletionTokens)
                .isGreaterThanOrEqualTo(secondReasoningTokens);
        assertThat(secondTotalTokens)
                .isEqualTo(secondPromptTokens + secondCompletionTokens);

        System.out.printf(
                Locale.ROOT,
                "provider=DEEPSEEK, mode=THINKING_TOOL_ROUND_TRIP, effort=LOW, "
                        + "firstRequestId=%s, secondRequestId=%s, model=%s, "
                        + "firstFinishReason=%s, secondFinishReason=%s, "
                        + "toolCallId=%s, functionName=%s, argumentsChars=%d, "
                        + "argumentsHash=%s, firstReasoningChars=%d, "
                        + "secondReasoningChars=%d, outputChars=%d, outputHash=%s, "
                        + "modelCalls=2, totalTokens=%d, reasoningTokens=%d, durationMs=%d%n",
                firstRoot.path("id").asText(),
                secondRoot.path("id").asText(),
                secondRoot.path("model").asText(),
                firstChoice.path("finish_reason").asText(),
                secondChoice.path("finish_reason").asText(),
                toolCallId,
                functionName,
                argumentsText.length(),
                sha256(argumentsText),
                firstReasoningContent.length(),
                secondReasoningContent.length(),
                finalContent.length(),
                sha256(finalContent),
                firstTotalTokens + secondTotalTokens,
                firstReasoningTokens + secondReasoningTokens,
                durationMs
        );
    }
    /** 构造不记录密钥和正文的官方JSON请求。 */
    private HttpRequest createJsonRequest(
            Map<String, Object> body,
            String apiKey
    ) throws JacksonException {
        return HttpRequest.newBuilder()
                .uri(ENDPOINT)
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        jsonMapper.writeValueAsString(body),
                        StandardCharsets.UTF_8
                ))
                .build();
    }

    /** 在测试Deadline内读取完整供应商响应。 */
    private HttpResponse<String> sendWithinDeadline(HttpRequest request) throws Exception {
        CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        try {
            return future.get(TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw exception;
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    /** 计算输出摘要，避免记录模型正文。 */
    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JVM不支持SHA-256", exception);
        }
    }
}