package com.leo.careerforgeai.model.infrastructure.deepseek.toolcalling;

import com.leo.careerforgeai.model.application.ToolCallingGateway;
import com.leo.careerforgeai.model.config.ModelProperties;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.toolcalling.AssistantToolCallsMessage;
import com.leo.careerforgeai.model.domain.toolcalling.FinalAnswerResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingMessage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingModelResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingRequest;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingTextMessage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallsResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolDefinition;
import com.leo.careerforgeai.model.domain.toolcalling.ToolResultMessage;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.model.infrastructure.deepseek.toolcalling.dto.DeepSeekToolCallingRequest;
import com.leo.careerforgeai.model.infrastructure.deepseek.toolcalling.dto.DeepSeekToolCallingResponse;
import com.leo.careerforgeai.shared.exception.BusinessException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 负责公共 Tool Calling 协议与 DeepSeek HTTP 协议之间的双向转换，并拒绝结构异常或无法安全归类的响应。
 */
@Component
@Slf4j
public class DeepSeekToolCallingClient implements ToolCallingGateway {

    private static final String FUNCTION_TYPE = "function";
    private static final String ASSISTANT_ROLE = "assistant";

    private final ModelProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public DeepSeekToolCallingClient(ModelProperties properties, JsonMapper jsonMapper, HttpClient httpClient) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.httpClient = httpClient;
    }

    @Override
    public ToolCallingModelResult call(ToolCallingRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "Tool Calling 请求不能为空");

        long startNanos = System.nanoTime();
        try {
            DeepSeekToolCallingRequest providerRequest = toProviderRequest(request);
            DeepSeekToolCallingResponse providerResponse = execute(providerRequest, request.timeout());
            ToolCallingModelResult result = toDomainResult(providerResponse);
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

            log.info("DeepSeek Tool Calling 完成，requestId={}, model={}, resultType={}, durationMs={}, totalTokens={}",
                    result.requestId(), result.model(), result.getClass().getSimpleName(), durationMs, result.usage().totalTokens());
            return result;
        } catch (HttpConnectTimeoutException e) {
            throw new ModelException(ModelErrorType.TIMEOUT, "连接模型供应商超时", e);
        } catch (HttpTimeoutException e) {
            throw new ModelException(ModelErrorType.TIMEOUT, "等待模型供应商响应超时", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelException(ModelErrorType.NETWORK_ERROR, "Tool Calling 模型调用被中断", e);
        } catch (IOException e) {
            log.error("DeepSeek Tool Calling 网络调用失败", e);
            throw new ModelException(ModelErrorType.NETWORK_ERROR, "Tool Calling 模型网络调用失败", e);
        }
    }

    private DeepSeekToolCallingRequest toProviderRequest(ToolCallingRequest request) {
        return new DeepSeekToolCallingRequest(
                properties.getName(),
                request.messages().stream().map(this::toProviderMessage).toList(),
                request.tools().stream().map(this::toProviderTool).toList(),
                request.toolChoiceMode().name().toLowerCase(Locale.ROOT),
                new DeepSeekToolCallingRequest.Thinking("disabled"),
                request.maxOutputTokens(),
                false
        );
    }

    private DeepSeekToolCallingRequest.Message toProviderMessage(ToolCallingMessage message) {
        if (message instanceof ToolCallingTextMessage textMessage) {
            return new DeepSeekToolCallingRequest.Message(
                    textMessage.role().name().toLowerCase(Locale.ROOT),
                    textMessage.content(),
                    null,
                    null
            );
        }

        if (message instanceof AssistantToolCallsMessage assistantMessage) {
            List<DeepSeekToolCallingRequest.ToolCall> calls = assistantMessage.toolCalls().stream()
                    .map(this::toProviderToolCall)
                    .toList();
            return new DeepSeekToolCallingRequest.Message(ASSISTANT_ROLE, "", calls, null);
        }

        ToolResultMessage resultMessage = (ToolResultMessage) message;
        return new DeepSeekToolCallingRequest.Message(
                "tool",
                resultMessage.content(),
                null,
                resultMessage.toolCallId()
        );
    }

    private DeepSeekToolCallingRequest.ToolCall toProviderToolCall(ToolCall toolCall) {
        return new DeepSeekToolCallingRequest.ToolCall(
                toolCall.id(),
                FUNCTION_TYPE,
                new DeepSeekToolCallingRequest.FunctionCall(toolCall.name(), toolCall.argumentsJson())
        );
    }

    private DeepSeekToolCallingRequest.Tool toProviderTool(ToolDefinition definition) {
        JsonNode schema = parseInputSchema(definition);
        DeepSeekToolCallingRequest.FunctionDefinition function = new DeepSeekToolCallingRequest.FunctionDefinition(
                definition.name(),
                definition.description(),
                false,
                schema
        );
        return new DeepSeekToolCallingRequest.Tool(FUNCTION_TYPE, function);
    }

    private JsonNode parseInputSchema(ToolDefinition definition) {
        try {
            JsonNode schema = jsonMapper.readTree(definition.inputSchemaJson());
            if (schema == null || !schema.isObject()) {
                throw new ModelException(ModelErrorType.CONFIGURATION_ERROR,
                        "工具输入 Schema 必须是 JSON 对象，toolName=" + definition.name());
            }
            return schema;
        } catch (JacksonException e) {
            throw new ModelException(ModelErrorType.CONFIGURATION_ERROR,
                    "工具输入 Schema 不是合法 JSON，toolName=" + definition.name(), e);
        }
    }

    private DeepSeekToolCallingResponse execute(DeepSeekToolCallingRequest providerRequest, Duration timeout)
            throws IOException, InterruptedException {
        String requestBody = serializeRequest(providerRequest);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(properties.getBaseUrl().resolve("/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw mapHttpError(response.statusCode());
        }

        try {
            DeepSeekToolCallingResponse providerResponse =
                    jsonMapper.readValue(response.body(), DeepSeekToolCallingResponse.class);
            if (providerResponse == null) {
                throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Tool Calling 模型响应为空");
            }
            return providerResponse;
        } catch (JacksonException e) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Tool Calling 模型响应不是合法 JSON", e);
        }
    }

    private ToolCallingModelResult toDomainResult(DeepSeekToolCallingResponse response) {
        validateEnvelope(response);

        DeepSeekToolCallingResponse.Choice choice = response.choices().getFirst();
        DeepSeekToolCallingResponse.Message message = choice.message();
        ModelUsage usage = toModelUsage(response.usage());

        return switch (choice.finishReason()) {
            case "stop" -> toFinalAnswer(response, message, usage);
            case "tool_calls" -> toToolCallsResult(response, message, usage);
            default -> throw new ModelException(ModelErrorType.INVALID_RESPONSE,
                    "Tool Calling 响应未正常完成，finishReason=" + choice.finishReason());
        };
    }

    private void validateEnvelope(DeepSeekToolCallingResponse response) {
        if (response.id() == null || response.id().isBlank()) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Tool Calling 响应缺少 requestId");
        }
        if (response.model() == null || response.model().isBlank()) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Tool Calling 响应缺少 model");
        }
        if (response.choices() == null || response.choices().size() != 1) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Tool Calling 响应必须包含一个 choice");
        }

        DeepSeekToolCallingResponse.Choice choice = response.choices().getFirst();
        if (choice == null || choice.message() == null) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Tool Calling 响应缺少 assistant 消息");
        }
        if (!ASSISTANT_ROLE.equals(choice.message().role())) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE,
                    "Tool Calling 响应消息角色非法，role=" + choice.message().role());
        }
        if (choice.finishReason() == null || choice.finishReason().isBlank()) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Tool Calling 响应缺少 finishReason");
        }
        if (response.usage() == null) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Tool Calling 响应缺少 Token usage");
        }
    }

    private ModelUsage toModelUsage(DeepSeekToolCallingResponse.Usage usage) {
        if (usage.promptTokens() < 0 || usage.completionTokens() < 0 || usage.totalTokens() < 0) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Tool Calling Token usage 不能为负数");
        }
        if (usage.promptTokens() + usage.completionTokens() != usage.totalTokens()) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Tool Calling Token usage 汇总不一致");
        }
        return new ModelUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
    }

    private FinalAnswerResult toFinalAnswer(
            DeepSeekToolCallingResponse response,
            DeepSeekToolCallingResponse.Message message,
            ModelUsage usage
    ) {
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE,
                    "finishReason=stop 时不能同时包含 toolCalls");
        }
        if (message.content() == null || message.content().isBlank()) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE,
                    "finishReason=stop 时最终回答不能为空");
        }
        return new FinalAnswerResult(response.id(), response.model(), message.content(), usage);
    }

    private ToolCallsResult toToolCallsResult(
            DeepSeekToolCallingResponse response,
            DeepSeekToolCallingResponse.Message message,
            ModelUsage usage
    ) {
        if (message.content() != null && !message.content().isBlank()) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE,
                    "finishReason=tool_calls 时不能同时包含最终回答");
        }
        if (message.toolCalls() == null || message.toolCalls().isEmpty()) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE,
                    "finishReason=tool_calls 时必须包含 toolCalls");
        }

        List<ToolCall> calls = new ArrayList<>(message.toolCalls().size());
        for (DeepSeekToolCallingResponse.ToolCall providerCall : message.toolCalls()) {
            calls.add(toDomainToolCall(providerCall));
        }

        try {
            return new ToolCallsResult(response.id(), response.model(), calls, usage);
        } catch (IllegalArgumentException e) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE,
                    "Tool Calling 响应中的工具调用不合法", e);
        }
    }

    private ToolCall toDomainToolCall(DeepSeekToolCallingResponse.ToolCall providerCall) {
        if (providerCall == null
                || providerCall.id() == null
                || providerCall.id().isBlank()
                || !FUNCTION_TYPE.equals(providerCall.type())
                || providerCall.function() == null
                || providerCall.function().name() == null
                || providerCall.function().name().isBlank()
                || providerCall.function().arguments() == null
                || providerCall.function().arguments().isBlank()) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE,
                    "Tool Calling 响应包含结构不完整的工具调用");
        }

        try {
            return new ToolCall(
                    providerCall.id(),
                    providerCall.function().name(),
                    providerCall.function().arguments()
            );
        } catch (IllegalArgumentException e) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE,
                    "Tool Calling 响应中的工具调用不合法", e);
        }
    }

    private String serializeRequest(DeepSeekToolCallingRequest request) {
        try {
            return jsonMapper.writeValueAsString(request);
        } catch (JacksonException e) {
            throw new ModelException(ModelErrorType.CONFIGURATION_ERROR,
                    "Tool Calling 请求序列化失败", e);
        }
    }

    private ModelException mapHttpError(int statusCode) {
        return switch (statusCode) {
            case 401 -> new ModelException(ModelErrorType.AUTHENTICATION_ERROR, "模型供应商鉴权失败");
            case 403 -> new ModelException(ModelErrorType.PERMISSION_ERROR, "模型供应商拒绝访问");
            case 404 -> new ModelException(ModelErrorType.MODEL_NOT_FOUND, "指定模型或模型接口不存在");
            case 408, 504 -> new ModelException(ModelErrorType.TIMEOUT, "模型供应商响应超时");
            case 429 -> new ModelException(ModelErrorType.RATE_LIMITED, "模型供应商请求频率受限");
            default -> {
                if (statusCode >= 500) {
                    yield new ModelException(ModelErrorType.PROVIDER_ERROR,
                            "模型供应商服务异常，statusCode=" + statusCode);
                }
                yield new ModelException(ModelErrorType.PROVIDER_ERROR,
                        "模型供应商拒绝请求，statusCode=" + statusCode);
            }
        };
    }
}