package com.leo.careerforgeai.model.infrastructure.kimi;

import com.leo.careerforgeai.model.application.ProviderModelClient;
import com.leo.careerforgeai.model.config.ModelRoutingProperties;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.routing.ModelCapability;
import com.leo.careerforgeai.model.domain.routing.ModelExecutionProfile;
import com.leo.careerforgeai.model.domain.routing.ReasoningMode;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEvent;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.model.exception.completion.ModelCompletionException;
import com.leo.careerforgeai.model.exception.completion.ModelCompletionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * @program: CareerForge-AI
 * @description: 将统一模型请求适配为Kimi Chat Completions协议并归一化响应和错误。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
@Component
@ConditionalOnProperty(prefix = "careerforge.model-routing.providers.kimi",
        name = "enabled", havingValue = "true")
@Slf4j
public final class KimiChatClient implements ProviderModelClient {

    private static final String PROVIDER_ID = "kimi";

    private final ModelRoutingProperties.Provider provider;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final URI chatUri;

    public KimiChatClient(ModelRoutingProperties properties, JsonMapper jsonMapper,
                          HttpClient httpClient) {
        if (properties == null) throw new IllegalArgumentException("properties不能为空");
        this.provider = properties.providers().get(PROVIDER_ID);
        if (provider == null) throw new IllegalArgumentException("缺少Kimi供应商配置");
        this.jsonMapper = java.util.Objects.requireNonNull(jsonMapper, "jsonMapper不能为空");
        this.httpClient = java.util.Objects.requireNonNull(httpClient, "httpClient不能为空");
        String baseUrl = provider.baseUrl().toString().replaceAll("/+$", "");
        this.chatUri = URI.create(baseUrl + "/chat/completions");
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public ModelResponse chat(ModelExecutionProfile profile, ModelRequest request) {
        validate(profile, request);
        ModelRequest effectiveRequest = effectiveRequest(profile, request);
        long startedNanos = System.nanoTime();
        try {
            HttpResponse<String> response = send(buildRequest(profile, effectiveRequest),
                    effectiveRequest.timeout());
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                throw mapHttpError(response.statusCode());
            }
            ModelResponse result = parseResponse(profile, response.body(), startedNanos);
            log.info("Kimi调用完成，requestId={}, model={}, durationMs={}, totalTokens={}",
                    result.requestId(), result.model(),
                    Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(),
                    result.usage() == null ? null : result.usage().totalTokens());
            return result;
        } catch (HttpConnectTimeoutException exception) {
            throw new ModelException(ModelErrorType.TIMEOUT, "连接Kimi超时", exception);
        } catch (HttpTimeoutException exception) {
            throw new ModelException(ModelErrorType.TIMEOUT, "等待Kimi完整响应超时", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelException(ModelErrorType.NETWORK_ERROR, "Kimi调用被中断", exception);
        } catch (IOException exception) {
            throw new ModelException(ModelErrorType.NETWORK_ERROR, "Kimi网络调用失败", exception);
        }
    }

    @Override
    public void stream(ModelExecutionProfile profile, ModelRequest request,
                       Consumer<ModelStreamEvent> eventConsumer) {
        throw new ModelException(ModelErrorType.CONFIGURATION_ERROR,
                "Kimi正式Adapter尚未声明Streaming能力");
    }

    private HttpRequest buildRequest(ModelExecutionProfile profile, ModelRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", profile.model());
        body.put("messages", request.messages().stream()
                .map(message -> Map.of(
                        "role", message.role().name().toLowerCase(Locale.ROOT),
                        "content", message.content()))
                .toList());
        body.put("thinking", Map.of("type",
                profile.reasoningMode() == ReasoningMode.DISABLED ? "disabled" : "enabled"));
        if (request.outputFormat() == ModelOutputFormat.JSON_OBJECT) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        body.put("max_tokens", request.maxOutputTokens());
        body.put("stream", false);

        try {
            return HttpRequest.newBuilder()
                    .uri(chatUri)
                    .timeout(request.timeout())
                    .header("Authorization", "Bearer " + provider.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
        } catch (JacksonException exception) {
            throw new ModelException(ModelErrorType.CONFIGURATION_ERROR,
                    "Kimi请求序列化失败", exception);
        }
    }

    private HttpResponse<String> send(HttpRequest request, Duration timeout)
            throws IOException, InterruptedException {
        var future = httpClient.sendAsync(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            HttpTimeoutException timeoutException = new HttpTimeoutException("等待Kimi完整响应超时");
            timeoutException.initCause(exception);
            throw timeoutException;
        } catch (InterruptedException exception) {
            future.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof IOException ioException) throw ioException;
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IOException("Kimi异步调用失败", cause);
        }
    }

    private ModelResponse parseResponse(ModelExecutionProfile profile, String body,
                                        long startedNanos) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(body);
        } catch (JacksonException exception) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE,
                    "Kimi响应不是合法JSON", exception);
        }
        if (!"chat.completion".equals(root.path("object").asText())
                || root.path("id").asText().isBlank()
                || !profile.model().equals(root.path("model").asText())
                || root.path("choices").size() != 1) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Kimi响应信封非法");
        }

        JsonNode choice = root.path("choices").path(0);
        String finishReason = choice.path("finish_reason").asText();
        String content = choice.path("message").path("content").asText();
        ModelUsage usage = parseUsage(root.path("usage"));
        ModelCompletionStatus completionStatus = completionStatus(finishReason);
        if (completionStatus != ModelCompletionStatus.COMPLETED) {
            throw new ModelCompletionException(completionStatus, finishReason,
                    root.path("id").asText(), root.path("model").asText(), usage,
                    Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(), content);
        }
        if (content.isBlank()) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Kimi返回内容为空");
        }
        return new ModelResponse(root.path("id").asText(), root.path("model").asText(),
                content, usage);
    }

    private ModelUsage parseUsage(JsonNode usage) {
        if (usage == null || !usage.isObject()) return null;
        long inputTokens = usage.path("prompt_tokens").asLong(-1);
        long outputTokens = usage.path("completion_tokens").asLong(-1);
        long totalTokens = usage.path("total_tokens").asLong(-1);
        if (inputTokens < 0 || outputTokens < 0 || totalTokens != inputTokens + outputTokens) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Kimi usage非法");
        }
        return new ModelUsage(inputTokens, outputTokens, totalTokens);
    }

    private ModelCompletionStatus completionStatus(String finishReason) {
        if (finishReason == null || finishReason.isBlank()) {
            return ModelCompletionStatus.UNKNOWN_INCOMPLETE;
        }
        return switch (finishReason.toLowerCase(Locale.ROOT)) {
            case "stop" -> ModelCompletionStatus.COMPLETED;
            case "length" -> ModelCompletionStatus.OUTPUT_TOKEN_LIMIT_REACHED;
            case "content_filter" -> ModelCompletionStatus.CONTENT_FILTERED;
            case "tool_calls" -> ModelCompletionStatus.TOOL_CALLS_REQUESTED;
            case "insufficient_system_resource" ->
                    ModelCompletionStatus.PROVIDER_RESOURCE_INTERRUPTED;
            default -> ModelCompletionStatus.UNKNOWN_INCOMPLETE;
        };
    }

    private ModelException mapHttpError(int statusCode) {
        return switch (statusCode) {
            case 401 -> new ModelException(ModelErrorType.AUTHENTICATION_ERROR, "Kimi鉴权失败");
            case 403 -> new ModelException(ModelErrorType.PERMISSION_ERROR, "Kimi拒绝访问");
            case 404 -> new ModelException(ModelErrorType.MODEL_NOT_FOUND, "Kimi模型或接口不存在");
            case 408, 504 -> new ModelException(ModelErrorType.TIMEOUT, "Kimi响应超时");
            case 429 -> new ModelException(ModelErrorType.RATE_LIMITED, "Kimi请求频率受限");
            default -> statusCode >= 500
                    ? new ModelException(ModelErrorType.PROVIDER_ERROR,
                    "Kimi服务异常，statusCode=" + statusCode)
                    : new ModelException(ModelErrorType.PROVIDER_REQUEST_REJECTED,
                    "Kimi拒绝请求，statusCode=" + statusCode);
        };
    }

    private void validate(ModelExecutionProfile profile, ModelRequest request) {
        if (profile == null || request == null) {
            throw new ModelException(ModelErrorType.CONFIGURATION_ERROR,
                    "Kimi Profile和请求不能为空");
        }
        if (!PROVIDER_ID.equals(profile.provider()) || !profile.enabled()) {
            throw new ModelException(ModelErrorType.CONFIGURATION_ERROR,
                    "Kimi Profile供应商错误或未启用");
        }
        if (!provider.enabled() || provider.apiKey().isBlank()) {
            throw new ModelException(ModelErrorType.CONFIGURATION_ERROR, "Kimi供应商未启用");
        }
        if (!profile.capabilities().contains(ModelCapability.CHAT)) {
            throw new ModelException(ModelErrorType.CONFIGURATION_ERROR,
                    "Kimi Profile不支持Chat");
        }
        if (profile.reasoningMode() == ReasoningMode.ADAPTIVE) {
            throw new ModelException(ModelErrorType.CONFIGURATION_ERROR,
                    "Kimi当前仅支持显式开启或关闭Thinking");
        }
        if (request.maxOutputTokens() > profile.maxOutputTokens()) {
            throw new ModelException(ModelErrorType.CONFIGURATION_ERROR,
                    "请求超过Kimi Profile输出Token上限");
        }
    }

    private ModelRequest effectiveRequest(ModelExecutionProfile profile, ModelRequest request) {
        Duration timeout = profile.timeout().compareTo(request.timeout()) < 0
                ? profile.timeout() : request.timeout();
        return request.withTimeout(timeout);
    }
}