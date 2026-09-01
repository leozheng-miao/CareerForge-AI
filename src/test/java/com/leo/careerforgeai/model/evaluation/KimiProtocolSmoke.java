package com.leo.careerforgeai.model.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.core.JacksonException;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 直接调用Kimi官方API验证模型、Structured Output、Thinking、Tool Calling、Streaming、完成原因和错误信封
 * @author: Miao Zheng
 * @date: 2026-09-01
 */
@EnabledIfEnvironmentVariable(named = "MOONSHOT_API_KEY", matches = ".+")
class KimiProtocolSmoke {

    private static final String BASE_URL =
            System.getenv().getOrDefault("KIMI_BASE_URL", "https://api.moonshot.cn/v1");
    private static final URI CHAT_COMPLETIONS_URI =
            URI.create(BASE_URL + "/chat/completions");
    private static final URI MODELS_URI =
            URI.create(BASE_URL + "/models");
    private static final String MODEL =
            System.getenv().getOrDefault("KIMI_MODEL", "kimi-k2.6");
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 验证账户真实可用模型列表包含配置模型。 */
    @Test
    void shouldListConfiguredModel() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(MODELS_URI)
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = sendWithinDeadline(request);
        assertThat(response.statusCode()).isEqualTo(200);

        JsonNode root = jsonMapper.readTree(response.body());
        assertThat(root.path("object").asText()).isEqualTo("list");
        assertThat(root.path("data").isArray()).isTrue();

        List<String> modelIds = new ArrayList<>();
        for (JsonNode model : root.path("data")) {
            assertThat(model.path("id").asText()).isNotBlank();
            modelIds.add(model.path("id").asText());
        }

        assertThat(modelIds).contains(MODEL);

        System.out.printf(
                Locale.ROOT,
                "provider=KIMI, mode=MODEL_LIST, httpStatus=%d, "
                        + "configuredModel=%s, configuredModelAvailable=true, "
                        + "kimiK3Available=%s, modelCount=%d%n",
                response.statusCode(),
                MODEL,
                modelIds.contains("kimi-k3"),
                modelIds.size()
        );
    }

    /** 验证非Thinking简单JSON Schema严格输出。 */
    @Test
    void shouldReturnStrictStructuredOutputWithoutThinking() throws Exception {
        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of(
                                        "role", "user",
                                        "content", "CareerForge后端检查完成，请按Schema返回结果。"
                                )
                        ),
                        "thinking", Map.of("type", "disabled"),
                        "response_format", Map.of(
                                "type", "json_schema",
                                "json_schema", Map.of(
                                        "name", "careerforge_check",
                                        "strict", true,
                                        "schema", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "status", Map.of(
                                                                "type", "string",
                                                                "enum", List.of("ok")
                                                        ),
                                                        "task", Map.of(
                                                                "type", "string",
                                                                "enum", List.of("careerforge")
                                                        )
                                                ),
                                                "required", List.of("status", "task"),
                                                "additionalProperties", false
                                        )
                                )
                        ),
                        "max_tokens", 128,
                        "stream", false
                ),
                apiKey()
        );

        long durationMs = elapsedMillis(startedNanos);
        JsonNode root = requireSuccessfulCompletion(response);
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");

        assertThat(choice.path("finish_reason").asText()).isEqualTo("stop");
        requireNoReasoning(message);

        String content = message.path("content").asText();
        assertThat(content).isNotBlank();

        JsonNode output = jsonMapper.readTree(content);
        assertThat(output.isObject()).isTrue();
        assertThat(output.size()).isEqualTo(2);
        assertThat(output.path("status").asText()).isEqualTo("ok");
        assertThat(output.path("task").asText()).isEqualTo("careerforge");

        long[] usage = requireUsage(root);

        printCompletion(
                "STRUCTURED_OUTPUT",
                response,
                root,
                choice,
                content,
                usage,
                durationMs
        );
    }

    /** 验证K2.6 Thinking内容与最终答案分离。 */
    @Test
    void shouldExposeReasoningWhenThinkingEnabled() throws Exception {
        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of(
                                        "role", "user",
                                        "content", "比较9.11和9.8哪个更大，只给出最终结论。"
                                )
                        ),
                        "thinking", Map.of("type", "enabled"),
                        "max_tokens", 512,
                        "stream", false
                ),
                apiKey()
        );
        long durationMs = elapsedMillis(startedNanos);

        JsonNode root = requireSuccessfulCompletion(response);
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");

        assertThat(choice.path("finish_reason").asText()).isEqualTo("stop");

        String content = message.path("content").asText();
        String reasoningContent = message.path("reasoning_content").asText();

        assertThat(content).isNotBlank();
        assertThat(reasoningContent).isNotBlank();
        assertThat(reasoningContent).isNotEqualTo(content);

        long[] usage = requireUsage(root);

        System.out.printf(
                Locale.ROOT,
                "provider=KIMI, mode=THINKING, model=%s, providerRequestId=%s, "
                        + "finishReason=%s, outputChars=%d, outputHash=%s, "
                        + "reasoningChars=%d, promptTokens=%d, completionTokens=%d, "
                        + "totalTokens=%d, cachedTokens=%d, durationMs=%d%n",
                root.path("model").asText(),
                root.path("id").asText(),
                choice.path("finish_reason").asText(),
                content.length(),
                sha256(content),
                reasoningContent.length(),
                usage[0],
                usage[1],
                usage[2],
                usage[3],
                durationMs
        );
    }

    /** 验证非Thinking Tool Calling信封和参数JSON。 */
    @Test
    void shouldReturnValidatedToolCallWithoutExecutingTool() throws Exception {
        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of(
                                        "role", "user",
                                        "content", "查询Java技能差距，必须调用lookup_skill_gap工具。"
                                )
                        ),
                        "thinking", Map.of("type", "disabled"),
                        "tools", skillGapTools(),
                        "tool_choice", Map.of(
                                "type", "function",
                                "function", Map.of("name", "lookup_skill_gap")
                        ),
                        "max_tokens", 128,
                        "stream", false
                ),
                apiKey()
        );
        long durationMs = elapsedMillis(startedNanos);

        JsonNode root = requireSuccessfulCompletion(response);
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");

        assertThat(choice.path("finish_reason").asText())
                .isEqualTo("tool_calls");
        requireNoReasoning(message);

        JsonNode toolCalls = message.path("tool_calls");
        assertThat(toolCalls.isArray()).isTrue();
        assertThat(toolCalls.size()).isEqualTo(1);

        JsonNode toolCall = toolCalls.path(0);
        assertThat(toolCall.path("id").asText()).isNotBlank();
        assertThat(toolCall.path("type").asText()).isEqualTo("function");
        assertThat(toolCall.path("function").path("name").asText())
                .isEqualTo("lookup_skill_gap");

        String argumentsText =
                toolCall.path("function").path("arguments").asText();
        JsonNode arguments = jsonMapper.readTree(argumentsText);

        assertThat(arguments.isObject()).isTrue();
        assertThat(arguments.size()).isEqualTo(1);
        assertThat(arguments.path("skill").asText()).isEqualTo("Java");

        long[] usage = requireUsage(root);

        System.out.printf(
                Locale.ROOT,
                "provider=KIMI, mode=TOOL_CALLING, thinking=DISABLED, "
                        + "providerRequestId=%s, model=%s, finishReason=%s, "
                        + "toolCallId=%s, functionName=%s, argumentsChars=%d, "
                        + "argumentsHash=%s, promptTokens=%d, completionTokens=%d, "
                        + "totalTokens=%d, cachedTokens=%d, durationMs=%d%n",
                root.path("id").asText(),
                root.path("model").asText(),
                choice.path("finish_reason").asText(),
                toolCall.path("id").asText(),
                toolCall.path("function").path("name").asText(),
                argumentsText.length(),
                sha256(argumentsText),
                usage[0],
                usage[1],
                usage[2],
                usage[3],
                durationMs
        );
    }

    /** 验证SSE事件、终止标记和最终usage。 */
    @Test
    void shouldReturnOrderedSseChunksWithTerminalUsage() throws Exception {
        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of("role", "user", "content", "只回复：OK")
                        ),
                        "thinking", Map.of("type", "disabled"),
                        "max_tokens", 32,
                        "stream", true,
                        "stream_options", Map.of("include_usage", true)
                ),
                apiKey(),
                "text/event-stream"
        );
        long durationMs = elapsedMillis(startedNanos);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers()
                .firstValue("Content-Type")
                .orElse("")).startsWith("text/event-stream");

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
        int contentChunks = 0;
        JsonNode finalUsage = null;

        for (int index = 0; index < payloads.size() - 1; index++) {
            JsonNode chunk = jsonMapper.readTree(payloads.get(index));
            assertThat(chunk.path("object").asText())
                    .isEqualTo("chat.completion.chunk");

            if (providerRequestId == null) {
                providerRequestId = chunk.path("id").asText();
                responseModel = chunk.path("model").asText();
            } else {
                assertThat(chunk.path("id").asText())
                        .isEqualTo(providerRequestId);
                assertThat(chunk.path("model").asText())
                        .isEqualTo(responseModel);
            }

            JsonNode usage = chunk.path("usage");
            if (!usage.isMissingNode() && !usage.isNull()) {
                finalUsage = usage;
            }

            JsonNode choices = chunk.path("choices");
            if (choices.isEmpty()) continue;

            assertThat(choices.size()).isEqualTo(1);
            JsonNode choice = choices.path(0);
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
                contentChunks++;
            }

            JsonNode finishNode = choice.path("finish_reason");
            if (!finishNode.isMissingNode() && !finishNode.isNull()) {
                assertThat(finishReason).isNull();
                finishReason = finishNode.asText();

                JsonNode choiceUsage = choice.path("usage");
                if (!choiceUsage.isMissingNode() && !choiceUsage.isNull()) {
                    finalUsage = choiceUsage;
                }
            }
        }

        assertThat(providerRequestId).isNotBlank();
        assertThat(responseModel).isEqualTo(MODEL);
        assertThat(finishReason).isEqualTo("stop");
        assertThat(contentChunks).isPositive();
        assertThat(content).isNotEmpty();
        assertThat(finalUsage).isNotNull();

        long promptTokens = finalUsage.path("prompt_tokens").asLong();
        long completionTokens = finalUsage.path("completion_tokens").asLong();
        long totalTokens = finalUsage.path("total_tokens").asLong();
        long cachedTokens = finalUsage.path("cached_tokens").asLong();

        assertThat(promptTokens).isPositive();
        assertThat(completionTokens).isPositive();
        assertThat(totalTokens)
                .isEqualTo(promptTokens + completionTokens);
        assertThat(cachedTokens).isNotNegative();

        System.out.printf(
                Locale.ROOT,
                "provider=KIMI, mode=STREAMING, thinking=DISABLED, "
                        + "providerRequestId=%s, model=%s, finishReason=%s, "
                        + "sseEvents=%d, contentChunks=%d, outputChars=%d, "
                        + "outputHash=%s, promptTokens=%d, completionTokens=%d, "
                        + "totalTokens=%d, cachedTokens=%d, durationMs=%d%n",
                providerRequestId,
                responseModel,
                finishReason,
                payloads.size(),
                contentChunks,
                content.length(),
                sha256(content.toString()),
                promptTokens,
                completionTokens,
                totalTokens,
                cachedTokens,
                durationMs
        );
    }

    /** 验证输出预算耗尽时返回finish_reason=length。 */
    @Test
    void shouldExposeLengthFinishReasonWhenTokenBudgetExhausted() throws Exception {
        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of(
                                        "role", "user",
                                        "content", "连续输出从1到100的编号及说明，不得提前结束。"
                                )
                        ),
                        "thinking", Map.of("type", "disabled"),
                        "max_tokens", 1,
                        "stream", false
                ),
                apiKey()
        );
        long durationMs = elapsedMillis(startedNanos);

        JsonNode root = requireSuccessfulCompletion(response);
        JsonNode choice = root.path("choices").path(0);

        assertThat(choice.path("finish_reason").asText())
                .isEqualTo("length");

        JsonNode contentNode = choice.path("message").path("content");
        String content = contentNode.isMissingNode() || contentNode.isNull()
                ? ""
                : contentNode.asText();
        long[] usage = requireUsage(root);

        printCompletion(
                "TOKEN_BUDGET_EXHAUSTED",
                response,
                root,
                choice,
                content,
                usage,
                durationMs
        );
    }

    /** 验证401状态及Kimi标准错误信封。 */
    @Test
    void shouldReturnStructuredAuthenticationError() throws Exception {
        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post(
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
        long durationMs = elapsedMillis(startedNanos);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers()
                .firstValue("Content-Type")
                .orElse("")).contains("application/json");
        assertThat(response.body()).doesNotContain(apiKey());

        JsonNode root = jsonMapper.readTree(response.body());
        JsonNode error = root.path("error");

        assertThat(error.isObject()).isTrue();
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.path("type").asText())
                .isIn(
                        "invalid_authentication_error",
                        "incorrect_api_key_error"
                );

        JsonNode codeNode = error.path("code");
        String errorCode = codeNode.isMissingNode() || codeNode.isNull()
                ? "UNKNOWN"
                : codeNode.asText();
        String providerRequestId = response.headers()
                .firstValue("x-request-id")
                .orElse("UNKNOWN");

        System.out.printf(
                Locale.ROOT,
                "provider=KIMI, mode=ERROR_PROTOCOL, httpStatus=%d, "
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

    /** 返回测试用只读技能差距工具定义。 */
    private List<Map<String, Object>> skillGapTools() {
        return List.of(
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
        );
    }

    /** 使用JSON响应发送官方Chat Completions请求。 */
    private HttpResponse<String> post(
            Map<String, Object> body,
            String apiKey
    ) throws Exception {
        return post(body, apiKey, "application/json");
    }

    /** 使用指定响应类型发送官方Chat Completions请求。 */
    private HttpResponse<String> post(
            Map<String, Object> body,
            String apiKey,
            String accept
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(CHAT_COMPLETIONS_URI)
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", accept)
                .POST(HttpRequest.BodyPublishers.ofString(
                        jsonMapper.writeValueAsString(body),
                        StandardCharsets.UTF_8
                ))
                .build();
        return sendWithinDeadline(request);
    }

    /** 校验普通成功信封并返回根节点。 */
    private JsonNode requireSuccessfulCompletion(
            HttpResponse<String> response
    ) throws JacksonException {
        assertThat(response.statusCode())
                .as("httpStatus=%s, responseChars=%s",
                        response.statusCode(), response.body().length())
                .isEqualTo(200);

        JsonNode root = jsonMapper.readTree(response.body());
        assertThat(root.path("object").asText()).isEqualTo("chat.completion");
        assertThat(root.path("id").asText()).isNotBlank();
        assertThat(root.path("model").asText()).isEqualTo(MODEL);
        assertThat(root.path("choices").size()).isEqualTo(1);
        return root;
    }

    /** 校验并返回prompt、completion、total和cached Token。 */
    private long[] requireUsage(JsonNode root) {
        JsonNode usage = root.path("usage");
        assertThat(usage.isObject()).isTrue();

        long promptTokens = usage.path("prompt_tokens").asLong();
        long completionTokens = usage.path("completion_tokens").asLong();
        long totalTokens = usage.path("total_tokens").asLong();
        long cachedTokens = usage.path("cached_tokens").asLong();

        assertThat(promptTokens).isPositive();
        assertThat(completionTokens).isPositive();
        assertThat(totalTokens)
                .isEqualTo(promptTokens + completionTokens);
        assertThat(cachedTokens).isNotNegative();

        return new long[]{
                promptTokens,
                completionTokens,
                totalTokens,
                cachedTokens
        };
    }

    /** 断言非Thinking响应没有泄露推理正文。 */
    private void requireNoReasoning(JsonNode message) {
        JsonNode reasoningContent = message.path("reasoning_content");
        assertThat(reasoningContent.isMissingNode()
                || reasoningContent.isNull()
                || reasoningContent.asText().isBlank()).isTrue();
    }

    /** 输出不包含模型正文的普通调用诊断。 */
    private void printCompletion(
            String mode,
            HttpResponse<String> response,
            JsonNode root,
            JsonNode choice,
            String content,
            long[] usage,
            long durationMs
    ) {
        System.out.printf(
                Locale.ROOT,
                "provider=KIMI, mode=%s, httpStatus=%d, providerRequestId=%s, "
                        + "model=%s, finishReason=%s, outputChars=%d, "
                        + "outputHash=%s, promptTokens=%d, completionTokens=%d, "
                        + "totalTokens=%d, cachedTokens=%d, durationMs=%d%n",
                mode,
                response.statusCode(),
                root.path("id").asText(),
                root.path("model").asText(),
                choice.path("finish_reason").asText(),
                content.length(),
                sha256(content),
                usage[0],
                usage[1],
                usage[2],
                usage[3],
                durationMs
        );
    }

    /** 在测试Deadline内读取完整供应商响应。 */
    private HttpResponse<String> sendWithinDeadline(
            HttpRequest request
    ) throws Exception {
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

    /** 返回当前Kimi测试密钥，不输出密钥正文。 */
    private String apiKey() {
        return System.getenv("MOONSHOT_API_KEY");
    }

    /** 计算本次调用耗时。 */
    private long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
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