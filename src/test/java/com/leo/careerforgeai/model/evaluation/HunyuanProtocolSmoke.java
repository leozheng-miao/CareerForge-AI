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
 * @description: 直接调用腾讯TokenHub混元API验证结构化输出、Thinking、Tool Calling、Streaming、完成原因和错误信封
 * @author: Miao Zheng
 * @date: 2026-09-01
 */
@EnabledIfEnvironmentVariable(
        named = "TENCENT_TOKENHUB_API_KEY",
        matches = ".+"
)
class HunyuanProtocolSmoke {

    private static final String BASE_URL = System.getenv()
            .getOrDefault(
                    "HUNYUAN_BASE_URL",
                    "https://tokenhub.tencentmaas.com/v1"
            );
    private static final URI CHAT_COMPLETIONS_URI =
            URI.create(BASE_URL + "/chat/completions");
    private static final String MODEL = System.getenv()
            .getOrDefault("HUNYUAN_MODEL", "hy3");
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 验证非Thinking严格JSON Schema输出。 */
    @Test
    void shouldReturnStrictStructuredOutputWithoutThinking()
            throws Exception {
        long startedNanos = System.nanoTime();

        HttpResponse<String> response = post(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of(
                                        "role", "user",
                                        "content",
                                        "CareerForge后端检查完成，请按Schema返回结果。"
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
                                                                "type",
                                                                "string",
                                                                "enum",
                                                                List.of("ok")
                                                        ),
                                                        "task", Map.of(
                                                                "type",
                                                                "string",
                                                                "enum",
                                                                List.of(
                                                                        "careerforge"
                                                                )
                                                        )
                                                ),
                                                "required",
                                                List.of(
                                                        "status",
                                                        "task"
                                                ),
                                                "additionalProperties",
                                                false
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

        assertThat(choice.path("finish_reason").asText())
                .isEqualTo("stop");
        requireNoReasoning(message);

        String content = message.path("content").asText();
        assertThat(content).isNotBlank();

        JsonNode output = jsonMapper.readTree(content);
        assertThat(output.isObject()).isTrue();
        assertThat(output.size()).isEqualTo(2);
        assertThat(output.path("status").asText()).isEqualTo("ok");
        assertThat(output.path("task").asText())
                .isEqualTo("careerforge");

        long[] usage = requireUsage(root);
        assertThat(usage[4]).isZero();

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

    /** 验证Thinking响应、推理正文和推理Token统计。 */
    @Test
    void shouldExposeReasoningWhenThinkingEnabled() throws Exception {
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
                        "thinking", Map.of(
                                "type", "enabled",
                                "budget_tokens", 512
                        ),
                        "reasoning_effort", "low",
                        "max_tokens", 768,
                        "stream", false
                ),
                apiKey()
        );
        long durationMs = elapsedMillis(startedNanos);

        JsonNode root = requireSuccessfulCompletion(response);
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");

        assertThat(choice.path("finish_reason").asText())
                .isEqualTo("stop");

        String content = message.path("content").asText();
        String reasoningContent = requireReasoning(message);

        assertThat(content).isNotBlank();
        assertThat(reasoningContent).isNotEqualTo(content);

        long[] usage = requireUsage(root);
        assertThat(usage[4]).isPositive();

        System.out.printf(
                Locale.ROOT,
                "provider=HUNYUAN, platform=TOKENHUB, mode=THINKING, "
                        + "providerRequestId=%s, model=%s, "
                        + "finishReason=%s, reasoningEffort=low, "
                        + "outputChars=%d, outputHash=%s, "
                        + "reasoningChars=%d, promptTokens=%d, "
                        + "completionTokens=%d, reasoningTokens=%d, "
                        + "totalTokens=%d, cachedTokens=%d, durationMs=%d%n",
                root.path("id").asText(),
                root.path("model").asText(),
                choice.path("finish_reason").asText(),
                content.length(),
                sha256(content),
                reasoningContent.length(),
                usage[0],
                usage[1],
                usage[4],
                usage[2],
                usage[3],
                durationMs
        );
    }

    /** 验证非Thinking强制工具调用信封和参数JSON。 */
    @Test
    void shouldReturnForcedToolCallWithoutThinking()
            throws Exception {
        long startedNanos = System.nanoTime();

        HttpResponse<String> response = post(
                Map.of(
                        "model", MODEL,
                        "messages", List.of(
                                Map.of(
                                        "role", "user",
                                        "content",
                                        "查询Java技能差距，必须调用lookup_skill_gap工具。"
                                )
                        ),
                        "thinking", Map.of("type", "disabled"),
                        "tools", skillGapTools(),
                        "tool_choice", "required",
                        "parallel_tool_calls", false,
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

        JsonNode toolCall = requireSingleSkillGapToolCall(message);
        String argumentsText = toolCall.path("function")
                .path("arguments")
                .asText();

        long[] usage = requireUsage(root);
        assertThat(usage[4]).isZero();

        System.out.printf(
                Locale.ROOT,
                "provider=HUNYUAN, platform=TOKENHUB, "
                        + "mode=TOOL_CALLING, thinking=DISABLED, "
                        + "providerRequestId=%s, model=%s, "
                        + "finishReason=%s, toolCallId=%s, "
                        + "functionName=%s, argumentsChars=%d, "
                        + "argumentsHash=%s, promptTokens=%d, "
                        + "completionTokens=%d, totalTokens=%d, "
                        + "cachedTokens=%d, durationMs=%d%n",
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

    /** 验证Thinking工具调用及Preserved Thinking完整回传。 */
    @Test
    void shouldPreserveReasoningAcrossToolRoundTrip()
            throws Exception {
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
                                "budget_tokens", 512
                        ),
                        "reasoning_effort", "low",
                        "preserved_thinking", true,
                        "tools", skillGapTools(),
                        "tool_choice", "auto",
                        "parallel_tool_calls", false,
                        "max_tokens", 768,
                        "stream", false
                ),
                apiKey()
        );

        JsonNode firstRoot =
                requireSuccessfulCompletion(firstResponse);
        JsonNode firstChoice =
                firstRoot.path("choices").path(0);
        JsonNode firstMessage =
                firstChoice.path("message");

        assertThat(firstChoice.path("finish_reason").asText())
                .isEqualTo("tool_calls");

        String firstReasoning = requireReasoning(firstMessage);
        JsonNode toolCall =
                requireSingleSkillGapToolCall(firstMessage);

        HttpResponse<String> secondResponse = post(
                Map.of(
                        "model", MODEL,
                        "messages", toolRoundTripMessages(
                                firstMessage,
                                toolCall
                        ),
                        "thinking", Map.of(
                                "type", "enabled",
                                "budget_tokens", 512
                        ),
                        "reasoning_effort", "low",
                        "preserved_thinking", true,
                        "tools", skillGapTools(),
                        "tool_choice", "auto",
                        "parallel_tool_calls", false,
                        "max_tokens", 768,
                        "stream", false
                ),
                apiKey()
        );
        long durationMs = elapsedMillis(startedNanos);

        JsonNode secondRoot =
                requireSuccessfulCompletion(secondResponse);
        JsonNode secondChoice =
                secondRoot.path("choices").path(0);
        JsonNode secondMessage =
                secondChoice.path("message");

        assertThat(secondChoice.path("finish_reason").asText())
                .isEqualTo("stop");

        String finalContent =
                secondMessage.path("content").asText();
        String secondReasoning =
                requireReasoning(secondMessage);

        assertThat(finalContent).isNotBlank();

        long[] firstUsage = requireUsage(firstRoot);
        long[] secondUsage = requireUsage(secondRoot);

        assertThat(firstUsage[4]).isPositive();
        assertThat(secondUsage[4]).isPositive();

        String argumentsText = toolCall.path("function")
                .path("arguments")
                .asText();

        System.out.printf(
                Locale.ROOT,
                "provider=HUNYUAN, platform=TOKENHUB, "
                        + "mode=PRESERVED_THINKING_TOOL_ROUND_TRIP, "
                        + "firstRequestId=%s, secondRequestId=%s, "
                        + "model=%s, firstFinishReason=%s, "
                        + "secondFinishReason=%s, toolCallId=%s, "
                        + "functionName=%s, argumentsChars=%d, "
                        + "argumentsHash=%s, firstReasoningChars=%d, "
                        + "secondReasoningChars=%d, outputChars=%d, "
                        + "outputHash=%s, promptTokens=%d, "
                        + "completionTokens=%d, reasoningTokens=%d, "
                        + "totalTokens=%d, cachedTokens=%d, durationMs=%d%n",
                firstRoot.path("id").asText(),
                secondRoot.path("id").asText(),
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
                firstUsage[4] + secondUsage[4],
                firstUsage[2] + secondUsage[2],
                firstUsage[3] + secondUsage[3],
                durationMs
        );
    }

    /** 验证非Thinking SSE事件、终止标记和最终usage。 */
    @Test
    void shouldReturnOrderedSseChunksWithTerminalUsage()
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
                        "thinking", Map.of("type", "disabled"),
                        "max_tokens", 32,
                        "stream", true,
                        "stream_options", Map.of(
                                "include_usage",
                                true
                        )
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
        assertThat(payloads.get(payloads.size() - 1))
                .isEqualTo("[DONE]");

        String providerRequestId = null;
        String responseModel = null;
        String finishReason = null;
        StringBuilder content = new StringBuilder();
        int contentChunks = 0;
        JsonNode finalUsage = null;

        for (int index = 0; index < payloads.size() - 1; index++) {
            JsonNode chunk =
                    jsonMapper.readTree(payloads.get(index));

            JsonNode error = chunk.path("error");
            assertThat(error.isMissingNode()).isTrue();

            assertThat(chunk.path("object").asText())
                    .isEqualTo("chat.completion.chunk");

            if (providerRequestId == null) {
                providerRequestId =
                        chunk.path("id").asText();
                responseModel =
                        chunk.path("model").asText();
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

            requireNoReasoning(delta);

            JsonNode contentNode = delta.path("content");
            if (!contentNode.isMissingNode()
                    && !contentNode.isNull()
                    && !contentNode.asText().isEmpty()) {
                content.append(contentNode.asText());
                contentChunks++;
            }

            JsonNode finishNode =
                    choice.path("finish_reason");
            if (!finishNode.isMissingNode()
                    && !finishNode.isNull()) {
                assertThat(finishReason).isNull();
                finishReason = finishNode.asText();
            }
        }

        assertThat(providerRequestId).isNotBlank();
        assertThat(responseModel).isEqualTo(MODEL);
        assertThat(finishReason).isEqualTo("stop");
        assertThat(contentChunks).isPositive();
        assertThat(content).isNotEmpty();
        assertThat(finalUsage).isNotNull();

        long[] usage = requireUsage(finalUsage);
        assertThat(usage[4]).isZero();

        System.out.printf(
                Locale.ROOT,
                "provider=HUNYUAN, platform=TOKENHUB, "
                        + "mode=STREAMING, thinking=DISABLED, "
                        + "providerRequestId=%s, model=%s, "
                        + "finishReason=%s, sseEvents=%d, "
                        + "contentChunks=%d, outputChars=%d, "
                        + "outputHash=%s, promptTokens=%d, "
                        + "completionTokens=%d, totalTokens=%d, "
                        + "cachedTokens=%d, durationMs=%d%n",
                providerRequestId,
                responseModel,
                finishReason,
                payloads.size(),
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
        requireNoReasoning(choice.path("message"));

        String content =
                nullableText(choice.path("message").path("content"));
        long[] usage = requireUsage(root);

        assertThat(usage[4]).isZero();

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

    /** 验证401状态及TokenHub标准错误信封。 */
    @Test
    void shouldReturnStructuredAuthenticationError()
            throws Exception {
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
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.path("message_zh").asText()).isNotBlank();
        assertThat(error.path("code").asText()).isNotBlank();
        assertThat(error.path("type").asText())
                .isEqualTo("gateway_error");
        assertThat(error.path("request_id").asText()).isNotBlank();

        System.out.printf(
                Locale.ROOT,
                "provider=HUNYUAN, platform=TOKENHUB, "
                        + "mode=ERROR_PROTOCOL, httpStatus=%d, "
                        + "providerRequestId=%s, errorType=%s, "
                        + "errorCode=%s, bodyChars=%d, bodyHash=%s, "
                        + "durationMs=%d%n",
                response.statusCode(),
                error.path("request_id").asText(),
                error.path("type").asText(),
                error.path("code").asText(),
                response.body().length(),
                sha256(response.body()),
                durationMs
        );
    }

    /** 校验单个技能差距工具调用并返回工具调用节点。 */
    private JsonNode requireSingleSkillGapToolCall(
            JsonNode message
    ) throws JacksonException {
        JsonNode toolCalls = message.path("tool_calls");

        assertThat(toolCalls.isArray()).isTrue();
        assertThat(toolCalls.size()).isEqualTo(1);

        JsonNode toolCall = toolCalls.path(0);

        assertThat(toolCall.path("id").asText()).isNotBlank();
        assertThat(toolCall.path("type").asText())
                .isEqualTo("function");
        assertThat(toolCall.path("function").path("name").asText())
                .isEqualTo("lookup_skill_gap");

        String argumentsText = toolCall.path("function")
                .path("arguments")
                .asText();
        JsonNode arguments =
                jsonMapper.readTree(argumentsText);

        assertThat(arguments.isObject()).isTrue();
        assertThat(arguments.size()).isEqualTo(1);
        assertThat(arguments.path("skill").asText())
                .isEqualTo("Java");

        return toolCall;
    }

    /** 构造第二轮消息并完整回传推理正文、签名块和工具调用。 */
    private List<Map<String, Object>> toolRoundTripMessages(
            JsonNode firstMessage,
            JsonNode toolCall
    ) {
        Map<String, Object> assistantMessage =
                new LinkedHashMap<>();

        assistantMessage.put("role", "assistant");
        assistantMessage.put(
                "content",
                firstMessage.path("content").isMissingNode()
                        || firstMessage.path("content").isNull()
                        ? null
                        : firstMessage.path("content").asText()
        );
        assistantMessage.put(
                "reasoning_content",
                firstMessage.path("reasoning_content").asText()
        );

        JsonNode reasoningDetails =
                firstMessage.path("reasoning_details");
        if (!reasoningDetails.isMissingNode()
                && !reasoningDetails.isNull()) {
            assistantMessage.put(
                    "reasoning_details",
                    reasoningDetails
            );
        }

        assistantMessage.put(
                "tool_calls",
                firstMessage.path("tool_calls")
        );

        return List.of(
                Map.of(
                        "role", "user",
                        "content",
                        "查询Java技能差距，必须调用lookup_skill_gap工具。"
                ),
                assistantMessage,
                Map.of(
                        "role", "tool",
                        "tool_call_id",
                        toolCall.path("id").asText(),
                        "name",
                        toolCall.path("function")
                                .path("name")
                                .asText(),
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
                                                        "type",
                                                        "string",
                                                        "enum",
                                                        List.of("Java")
                                                )
                                        ),
                                        "required",
                                        List.of("skill"),
                                        "additionalProperties",
                                        false
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
                .header(
                        "Authorization",
                        "Bearer " + apiKey
                )
                .header(
                        "Content-Type",
                        "application/json"
                )
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

        JsonNode root =
                jsonMapper.readTree(response.body());

        assertThat(root.path("object").asText())
                .isEqualTo("chat.completion");
        assertThat(root.path("id").asText()).isNotBlank();
        assertThat(root.path("model").asText())
                .isEqualTo(MODEL);
        assertThat(root.path("choices").size())
                .isEqualTo(1);

        return root;
    }

    /** 校验并返回输入、输出、总量、缓存和推理Token。 */
    private long[] requireUsage(JsonNode root) {
        JsonNode usage = root.path("usage");

        assertThat(usage.isObject()).isTrue();

        long promptTokens =
                usage.path("prompt_tokens").asLong();
        long completionTokens =
                usage.path("completion_tokens").asLong();
        long totalTokens =
                usage.path("total_tokens").asLong();
        long nestedCachedTokens = usage
                .path("prompt_tokens_details")
                .path("cached_tokens")
                .asLong();
        long cacheReadTokens =
                usage.path("cache_read_tokens").asLong();
        long cachedTokens =
                Math.max(nestedCachedTokens, cacheReadTokens);
        long reasoningTokens = usage
                .path("completion_tokens_details")
                .path("reasoning_tokens")
                .asLong();

        assertThat(promptTokens).isPositive();
        assertThat(completionTokens).isPositive();
        assertThat(totalTokens)
                .isEqualTo(promptTokens + completionTokens);
        assertThat(cachedTokens).isNotNegative();
        assertThat(reasoningTokens).isNotNegative();
        assertThat(reasoningTokens)
                .isLessThanOrEqualTo(completionTokens);

        return new long[]{
                promptTokens,
                completionTokens,
                totalTokens,
                cachedTokens,
                reasoningTokens
        };
    }

    /** 断言响应未返回推理正文。 */
    private void requireNoReasoning(JsonNode message) {
        JsonNode reasoningContent =
                message.path("reasoning_content");

        assertThat(
                reasoningContent.isMissingNode()
                        || reasoningContent.isNull()
                        || reasoningContent.asText().isBlank()
        ).isTrue();
    }

    /** 校验Thinking响应并返回推理正文。 */
    private String requireReasoning(JsonNode message) {
        String reasoningContent =
                message.path("reasoning_content").asText();

        assertThat(reasoningContent).isNotBlank();
        return reasoningContent;
    }

    /** 安全读取可能为空的文本字段。 */
    private String nullableText(JsonNode node) {
        return node.isMissingNode() || node.isNull()
                ? ""
                : node.asText();
    }

    /** 输出不包含模型正文和思维链的普通调用诊断。 */
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
                "provider=HUNYUAN, platform=TOKENHUB, mode=%s, "
                        + "httpStatus=%d, providerRequestId=%s, "
                        + "model=%s, finishReason=%s, outputChars=%d, "
                        + "outputHash=%s, promptTokens=%d, "
                        + "completionTokens=%d, reasoningTokens=%d, "
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
                usage[4],
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

    /** 返回当前腾讯TokenHub测试密钥，不输出密钥正文。 */
    private String apiKey() {
        return System.getenv(
                "TENCENT_TOKENHUB_API_KEY"
        );
    }

    /** 计算本次调用耗时。 */
    private long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(
                System.nanoTime() - startedNanos
        ).toMillis();
    }

    /** 计算输出摘要，避免记录模型正文和思维链。 */
    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(
                            content.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前JVM不支持SHA-256",
                    exception
            );
        }
    }
}