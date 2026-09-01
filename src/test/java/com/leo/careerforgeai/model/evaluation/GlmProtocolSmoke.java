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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 直接调用GLM官方API验证Structured Output、Thinking、Tool Calling、Streaming、完成原因和错误信封
 * @author: Miao Zheng
 * @date: 2026-09-01
 */
@EnabledIfEnvironmentVariable(named = "ZHIPUAI_API_KEY", matches = ".+")
class GlmProtocolSmoke {

    private static final String BASE_URL = System.getenv()
            .getOrDefault(
                    "GLM_BASE_URL",
                    "https://open.bigmodel.cn/api/paas/v4"
            );
    private static final URI CHAT_COMPLETIONS_URI =
            URI.create(BASE_URL + "/chat/completions");
    private static final String MODEL = System.getenv()
            .getOrDefault("GLM_MODEL", "glm-5.3-flash");
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 验证Thinking模式下的JSON Object结构化输出。 */
    @Test
    void shouldReturnValidatedStructuredOutput() throws Exception {
        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of(
                                        "role", "system",
                                        "content",
                                        """
                                        只返回JSON对象，必须且只能包含：
                                        {"status":"ok","task":"careerforge"}
                                        """
                                ),
                                Map.of(
                                        "role", "user",
                                        "content", "返回检查结果。"
                                )
                        ),
                        "thinking", Map.of("type", "enabled"),
                        "reasoning_effort", "low",
                        "response_format", Map.of("type", "json_object"),
                        "max_tokens", 256,
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

    /** 验证GLM强制Thinking及reasoning_content与最终答案分离。 */
    @Test
    void shouldExposeReasoningWithLowEffort() throws Exception {
        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of(
                                        "role", "user",
                                        "content",
                                        "比较9.11和9.8哪个更大，只给出最终结论。"
                                )
                        ),
                        "thinking", Map.of("type", "enabled"),
                        "reasoning_effort", "low",
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
        String reasoningContent =
                nullableText(message.path("reasoning_content"));

        assertThat(content).isNotBlank();
        if (!reasoningContent.isBlank()) {
            assertThat(reasoningContent).isNotEqualTo(content);
        }

        long[] usage = requireUsage(root);

        System.out.printf(
                Locale.ROOT,
                "provider=GLM, mode=THINKING, model=%s, "
                        + "providerRequestId=%s, finishReason=%s, "
                        + "reasoningEffort=low, outputChars=%d, outputHash=%s, "
                        + "reasoningChars=%d, promptTokens=%d, "
                        + "completionTokens=%d, totalTokens=%d, "
                        + "cachedTokens=%d, durationMs=%d%n",
                root.path("model").asText(),
                providerRequestId(root),
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

    /** 验证Thinking工具调用及第二轮reasoning_content完整回传。 */
    @Test
    void shouldPreserveReasoningAcrossToolRoundTrip() throws Exception {
        long startedNanos = System.nanoTime();

        HttpResponse<String> firstResponse = post(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of(
                                        "role", "user",
                                        "content",
                                        "查询Java技能差距，必须调用lookup_skill_gap工具。"
                                )
                        ),
                        "thinking", Map.of(
                                "type", "enabled",
                                "clear_thinking", false
                        ),
                        "reasoning_effort", "low",
                        "tools", skillGapTools(),
                        "tool_choice", "auto",
                        "max_tokens", 512,
                        "stream", false
                ),
                apiKey()
        );

        JsonNode firstRoot = requireSuccessfulCompletion(firstResponse);
        JsonNode firstChoice = firstRoot.path("choices").path(0);
        JsonNode firstMessage = firstChoice.path("message");

        assertThat(firstChoice.path("finish_reason").asText())
                .isEqualTo("tool_calls");

        String firstReasoning =
                nullableText(firstMessage.path("reasoning_content"));
        JsonNode toolCalls = firstMessage.path("tool_calls");

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

        List<Map<String, Object>> roundTripMessages =
                toolRoundTripMessages(firstMessage, toolCall);

        assertThat(roundTripMessages.get(1).get("reasoning_content"))
                .isEqualTo(firstReasoning);

        HttpResponse<String> secondResponse = post(
                Map.of(
                        "model", MODEL,
                        "messages", roundTripMessages,
                        "thinking", Map.of(
                                "type", "enabled",
                                "clear_thinking", false
                        ),
                        "reasoning_effort", "low",
                        "tools", skillGapTools(),
                        "tool_choice", "auto",
                        "max_tokens", 512,
                        "stream", false
                ),
                apiKey()
        );
        long durationMs = elapsedMillis(startedNanos);

        JsonNode secondRoot = requireSuccessfulCompletion(secondResponse);
        JsonNode secondChoice = secondRoot.path("choices").path(0);
        JsonNode secondMessage = secondChoice.path("message");

        assertThat(secondChoice.path("finish_reason").asText())
                .isEqualTo("stop");

        String finalContent = secondMessage.path("content").asText();
        assertThat(finalContent).isNotBlank();

        String secondReasoning =
                nullableText(secondMessage.path("reasoning_content"));
        long[] firstUsage = requireUsage(firstRoot);
        long[] secondUsage = requireUsage(secondRoot);

        System.out.printf(
                Locale.ROOT,
                "provider=GLM, mode=TOOL_ROUND_TRIP, "
                        + "firstRequestId=%s, secondRequestId=%s, model=%s, "
                        + "firstFinishReason=%s, secondFinishReason=%s, "
                        + "toolCallId=%s, functionName=%s, "
                        + "argumentsChars=%d, argumentsHash=%s, "
                        + "firstReasoningChars=%d, secondReasoningChars=%d, "
                        + "outputChars=%d, outputHash=%s, "
                        + "promptTokens=%d, completionTokens=%d, "
                        + "totalTokens=%d, cachedTokens=%d, durationMs=%d%n",
                providerRequestId(firstRoot),
                providerRequestId(secondRoot),
                secondRoot.path("model").asText(),
                firstChoice.path("finish_reason").asText(),
                secondChoice.path("finish_reason").asText(),
                toolCall.path("id").asText(),
                toolCall.path("function").path("name").asText(),
                argumentsText.length(),
                sha256(argumentsText),
                firstReasoning.length(),
                secondReasoning.length(),
                finalContent.length(),
                sha256(finalContent),
                firstUsage[0] + secondUsage[0],
                firstUsage[1] + secondUsage[1],
                firstUsage[2] + secondUsage[2],
                firstUsage[3] + secondUsage[3],
                durationMs
        );
    }

    /** 验证SSE事件顺序、Thinking增量、终止标记和最终usage。 */
    @Test
    void shouldReturnOrderedSseChunksWithReasoningAndUsage()
            throws Exception {
        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of(
                                        "role", "user",
                                        "content", "只回复：OK"
                                )
                        ),
                        "thinking", Map.of("type", "enabled"),
                        "reasoning_effort", "low",
                        "max_tokens", 1024,
                        "stream", true
                ),
                apiKey(),
                "text/event-stream"
        );
        long durationMs = elapsedMillis(startedNanos);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers()
                .firstValue("Content-Type")
                .orElse(""))
                .startsWith("text/event-stream");

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
        StringBuilder reasoning = new StringBuilder();
        StringBuilder content = new StringBuilder();
        int reasoningChunks = 0;
        int contentChunks = 0;
        JsonNode finalUsage = null;

        for (int index = 0; index < payloads.size() - 1; index++) {
            JsonNode chunk = jsonMapper.readTree(payloads.get(index));

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
                finalUsage = chunk;
            }

            JsonNode choices = chunk.path("choices");
            if (choices.isEmpty()) continue;

            assertThat(choices.size()).isEqualTo(1);
            JsonNode choice = choices.path(0);
            JsonNode delta = choice.path("delta");

            JsonNode reasoningNode = delta.path("reasoning_content");
            if (!reasoningNode.isMissingNode()
                    && !reasoningNode.isNull()
                    && !reasoningNode.asText().isEmpty()) {
                reasoning.append(reasoningNode.asText());
                reasoningChunks++;
            }

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
            }
        }

        assertThat(providerRequestId).isNotBlank();
        assertThat(responseModel).isEqualTo(MODEL);
        assertThat(finishReason).isEqualTo("stop");
        assertThat(reasoningChunks).isNotNegative();
        assertThat(contentChunks).isPositive();
        assertThat(content).isNotEmpty();
        assertThat(finalUsage).isNotNull();

        long[] usage = requireUsage(finalUsage);

        System.out.printf(
                Locale.ROOT,
                "provider=GLM, mode=STREAMING, thinking=ENABLED, "
                        + "reasoningEffort=low, providerRequestId=%s, "
                        + "model=%s, finishReason=%s, sseEvents=%d, "
                        + "reasoningChunks=%d, reasoningChars=%d, "
                        + "contentChunks=%d, outputChars=%d, outputHash=%s, "
                        + "promptTokens=%d, completionTokens=%d, "
                        + "totalTokens=%d, cachedTokens=%d, durationMs=%d%n",
                providerRequestId,
                responseModel,
                finishReason,
                payloads.size(),
                reasoningChunks,
                reasoning.length(),
                contentChunks,
                content.length(),
                sha256(content.toString()),
                usage[0],
                usage[1],
                usage[2],
                usage[3],
                durationMs
        );
    }

    /** 验证输出预算耗尽时返回finish_reason=length。 */
    @Test
    void shouldExposeLengthFinishReasonWhenTokenBudgetExhausted()
            throws Exception {
        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of(
                                        "role", "user",
                                        "content",
                                        "连续输出从1到100的编号及说明，不得提前结束。"
                                )
                        ),
                        "thinking", Map.of("type", "enabled"),
                        "reasoning_effort", "low",
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

        JsonNode message = choice.path("message");
        String content = nullableText(message.path("content"));
        String reasoning = nullableText(message.path("reasoning_content"));

        assertThat(content.length() + reasoning.length()).isPositive();

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

    /** 验证401状态及GLM标准错误信封。 */
    @Test
    void shouldReturnStructuredAuthenticationError() throws Exception {
        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of(
                                        "role", "user",
                                        "content", "只回复OK"
                                )
                        ),
                        "thinking", Map.of("type", "enabled"),
                        "reasoning_effort", "low",
                        "max_tokens", 16,
                        "stream", false
                ),
                "cp0-invalid-api-key"
        );
        long durationMs = elapsedMillis(startedNanos);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers()
                .firstValue("Content-Type")
                .orElse(""))
                .contains("application/json");
        assertThat(response.body()).doesNotContain(apiKey());

        JsonNode root = jsonMapper.readTree(response.body());
        JsonNode error = root.path("error");

        assertThat(error.isObject()).isTrue();
        assertThat(error.path("code").asText()).isNotBlank();
        assertThat(error.path("message").asText()).isNotBlank();

        System.out.printf(
                Locale.ROOT,
                "provider=GLM, mode=ERROR_PROTOCOL, httpStatus=%d, "
                        + "errorCode=%s, bodyChars=%d, bodyHash=%s, "
                        + "durationMs=%d%n",
                response.statusCode(),
                error.path("code").asText(),
                response.body().length(),
                sha256(response.body()),
                durationMs
        );
    }

    /** 验证GLM-5.3系列拒绝关闭Thinking。 */
    @Test
    void shouldRejectDisabledThinking() throws Exception {
        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of(
                                        "role", "user",
                                        "content", "只回复OK"
                                )
                        ),
                        "thinking", Map.of("type", "disabled"),
                        "max_tokens", 16,
                        "stream", false
                ),
                apiKey()
        );
        long durationMs = elapsedMillis(startedNanos);

        assertThat(response.statusCode()).isEqualTo(400);

        JsonNode root = jsonMapper.readTree(response.body());
        JsonNode error = root.path("error");

        assertThat(error.isObject()).isTrue();
        assertThat(error.path("code").asText()).isNotBlank();
        assertThat(error.path("message").asText()).isNotBlank();

        System.out.printf(
                Locale.ROOT,
                "provider=GLM, mode=THINKING_DISABLED_REJECTED, "
                        + "httpStatus=%d, model=%s, errorCode=%s, "
                        + "bodyChars=%d, bodyHash=%s, durationMs=%d%n",
                response.statusCode(),
                MODEL,
                error.path("code").asText(),
                response.body().length(),
                sha256(response.body()),
                durationMs
        );
    }

    /** 返回第二轮工具结果消息，并完整保留首轮推理正文。 */
    private List<Map<String, Object>> toolRoundTripMessages(
            JsonNode firstMessage,
            JsonNode toolCall
    ) {
        Map<String, Object> assistantMessage = new LinkedHashMap<>();
        assistantMessage.put("role", "assistant");
        assistantMessage.put(
                "content",
                firstMessage.path("content").isNull()
                        || firstMessage.path("content").isMissingNode()
                        ? null
                        : firstMessage.path("content").asText()
        );
        assistantMessage.put(
                "reasoning_content",
                firstMessage.path("reasoning_content").asText()
        );
        assistantMessage.put("tool_calls", firstMessage.path("tool_calls"));

        return List.of(
                Map.of(
                        "role", "user",
                        "content",
                        "查询Java技能差距，必须调用lookup_skill_gap工具。"
                ),
                assistantMessage,
                Map.of(
                        "role", "tool",
                        "tool_call_id", toolCall.path("id").asText(),
                        "content",
                        """
                        {"skill":"Java","gap":"并发基础"}
                        """
                )
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
                .as(
                        "httpStatus=%s, responseChars=%s",
                        response.statusCode(),
                        response.body().length()
                )
                .isEqualTo(200);

        JsonNode root = jsonMapper.readTree(response.body());

        assertThat(root.path("model").asText()).isEqualTo(MODEL);
        assertThat(providerRequestId(root)).isNotBlank();
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
        long cachedTokens = usage.path("prompt_tokens_details")
                .path("cached_tokens")
                .asLong();

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

    /** 校验Thinking响应并返回推理正文。 */
    private String requireReasoning(JsonNode message) {
        String reasoningContent =
                message.path("reasoning_content").asText();
        assertThat(reasoningContent).isNotBlank();
        return reasoningContent;
    }

    /** 优先返回供应商request_id，缺失时回退响应id。 */
    private String providerRequestId(JsonNode root) {
        String requestId = root.path("request_id").asText();
        return requestId.isBlank()
                ? root.path("id").asText()
                : requestId;
    }

    /** 安全读取可能为空的文本字段。 */
    private String nullableText(JsonNode node) {
        return node.isMissingNode() || node.isNull()
                ? ""
                : node.asText();
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
                "provider=GLM, mode=%s, httpStatus=%d, "
                        + "providerRequestId=%s, model=%s, "
                        + "finishReason=%s, outputChars=%d, outputHash=%s, "
                        + "promptTokens=%d, completionTokens=%d, "
                        + "totalTokens=%d, cachedTokens=%d, durationMs=%d%n",
                mode,
                response.statusCode(),
                providerRequestId(root),
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
        CompletableFuture<HttpResponse<String>> future =
                httpClient.sendAsync(
                        request,
                        HttpResponse.BodyHandlers.ofString(
                                StandardCharsets.UTF_8
                        )
                );
        try {
            return future.get(
                    TIMEOUT.toNanos(),
                    TimeUnit.NANOSECONDS
            );
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw exception;
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    /** 返回当前GLM测试密钥，不输出密钥正文。 */
    private String apiKey() {
        return System.getenv("ZHIPUAI_API_KEY");
    }

    /** 计算本次调用耗时。 */
    private long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(
                System.nanoTime() - startedNanos
        ).toMillis();
    }

    /** 计算输出摘要，避免记录模型正文和推理正文。 */
    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前JVM不支持SHA-256",
                    exception
            );
        }
    }
}