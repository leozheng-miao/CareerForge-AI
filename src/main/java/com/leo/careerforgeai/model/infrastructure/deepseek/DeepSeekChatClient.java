package com.leo.careerforgeai.model.infrastructure.deepseek;

import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.model.infrastructure.deepseek.dto.DeepSeekChatRequest;
import com.leo.careerforgeai.model.infrastructure.deepseek.dto.DeepSeekChatResponse;
import com.leo.careerforgeai.model.infrastructure.deepseek.dto.DeepSeekStreamChunk;
import com.leo.careerforgeai.model.infrastructure.deepseek.stream.DeepSeekSseParser;
import com.leo.careerforgeai.shared.exception.BusinessException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.config.ModelProperties;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEvent;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEventType;
import com.leo.careerforgeai.model.domain.ModelUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.leo.careerforgeai.model.exception.completion.ModelCompletionException;
import com.leo.careerforgeai.model.exception.completion.ModelCompletionStatus;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.concurrent.TimeoutException;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-28 17:06
 **/
@Component
@Slf4j
public class DeepSeekChatClient implements ModelGateway {
    private final ModelProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final DeepSeekSseParser sseParser;

    public DeepSeekChatClient(ModelProperties properties,
                              JsonMapper jsonMapper,
                              DeepSeekSseParser sseParser,
                              HttpClient httpClient) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.sseParser = sseParser;
        this.httpClient = httpClient;
    }


    @Override
    public ModelResponse chat(ModelRequest request) {
        try {
            DeepSeekChatRequest providerRequest = toProviderRequest(request, false);
            DeepSeekChatResponse providerResponse = execute(providerRequest, request.timeout());
            return toModelResponse(providerResponse);
        } catch (HttpConnectTimeoutException e) {
            throw new ModelException(ModelErrorType.TIMEOUT, "连接模型供应商超时", e);
        } catch (HttpTimeoutException e) {
            throw new ModelException(ModelErrorType.TIMEOUT, "等待模型供应商响应超时", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelException(ModelErrorType.NETWORK_ERROR, "大模型调用被中断", e);
        } catch (IOException e) {
            log.error("DeepSeek网络调用失败", e);
            throw new ModelException(ModelErrorType.NETWORK_ERROR, "大模型网络调用失败", e);
        }
    }

    @Override
    public void stream(ModelRequest request, Consumer<ModelStreamEvent> eventConsumer) {
        if (eventConsumer == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "流式事件消费者不能为空");
        }
        DeepSeekChatRequest providerRequest = toProviderRequest(request, true);
        String requestID = UUID.randomUUID().toString();
        eventConsumer.accept(new ModelStreamEvent(
                ModelStreamEventType.START,
                requestID,
                null,
                null,
                null
        ));
        StreamState state = new StreamState();
        try {
            executeStream(providerRequest, request.timeout(), chunk ->
                    handleStreamChunk(chunk, requestID, eventConsumer, state));
            ModelCompletionStatus completionStatus =
                    mapCompletionStatus(state.finishReason);
            if (completionStatus != ModelCompletionStatus.COMPLETED) {
                long durationMs = Duration.ofNanos(
                        System.nanoTime() - state.startNanos
                ).toMillis();
                throw new ModelCompletionException(
                        completionStatus,
                        state.finishReason,
                        state.providerRequestId,
                        state.model,
                        state.usage,
                        durationMs,
                        state.output.toString()
                );
            }

            eventConsumer.accept(new ModelStreamEvent(
                    ModelStreamEventType.COMPLETED,
                    requestID,
                    null,
                    state.usage,
                    null
            ));
            long durationMs = Duration.ofNanos(System.nanoTime() - state.startNanos).toMillis();
            Long totalTokens = state.usage == null ? null : state.usage.totalTokens();
            log.info("DeepSeek流式调用完成，requestId={}, providerRequestId={}, model={}, durationMs={}, totalTokens={}",
                    requestID, state.providerRequestId, state.model, durationMs, totalTokens);
        } catch (HttpConnectTimeoutException e) {
            emitClassifiedError(
                    requestID,
                    new ModelException(ModelErrorType.TIMEOUT, "连接模型供应商超时", e),
                    eventConsumer,
                    state
            );
        } catch (HttpTimeoutException e) {
            emitClassifiedError(
                    requestID,
                    new ModelException(ModelErrorType.TIMEOUT, "等待模型供应商响应超时", e),
                    eventConsumer,
                    state
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            emitClassifiedError(
                    requestID,
                    new ModelException(ModelErrorType.NETWORK_ERROR, "大模型流式调用被中断", e),
                    eventConsumer,
                    state
            );
        } catch (IOException e) {
            emitClassifiedError(
                    requestID,
                    new ModelException(ModelErrorType.NETWORK_ERROR, "大模型流式网络调用失败", e),
                    eventConsumer,
                    state
            );
        } catch (ModelException e) {
            emitClassifiedError(requestID, e, eventConsumer, state);
        } catch (BusinessException e) {
            ModelException exception = new ModelException(ModelErrorType.PROVIDER_ERROR, e.getMessage(), e);
            emitClassifiedError(requestID, exception, eventConsumer, state);
        }
    }

    /**
     * 执行非流式模型请求。
     *
     * @param deepSeekChatRequest DeepSeek请求
     * @param timeout             本次调用总超时
     * @return 已正常完成的供应商响应
     * @throws IOException          网络调用失败
     * @throws InterruptedException 调用线程被中断
     */
    private DeepSeekChatResponse execute(
            DeepSeekChatRequest deepSeekChatRequest,
            Duration timeout
    ) throws IOException, InterruptedException {
        long startNanos = System.nanoTime();
        String jsonBody = serializeRequest(deepSeekChatRequest);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(properties.getBaseUrl().resolve("/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header(
                        "Authorization",
                        "Bearer " + properties.getApiKey()
                )
                .POST(HttpRequest.BodyPublishers.ofString(
                        jsonBody,
                        StandardCharsets.UTF_8
                ))
                .build();

        HttpResponse<String> response =
                sendWithinDeadline(request, timeout);

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode > 299) {
            throw mapHttpError(statusCode);
        }

        DeepSeekChatResponse deepSeekChatResponse;
        try {
            deepSeekChatResponse = jsonMapper.readValue(
                    response.body(),
                    DeepSeekChatResponse.class
            );
        } catch (JacksonException exception) {
            throw new ModelException(
                    ModelErrorType.INVALID_RESPONSE,
                    "模型响应不是合法JSON",
                    exception
            );
        }

        if (deepSeekChatResponse == null) {
            throw new ModelException(
                    ModelErrorType.INVALID_RESPONSE,
                    "大模型响应为空"
            );
        }

        List<DeepSeekChatResponse.Choice> choices =
                deepSeekChatResponse.choices();
        if (choices == null
                || choices.isEmpty()
                || choices.getFirst() == null) {
            throw new ModelException(
                    ModelErrorType.INVALID_RESPONSE,
                    "大模型返回消息为空"
            );
        }

        DeepSeekChatResponse.Choice choice = choices.getFirst();
        String finishReason = choice.finishReason();
        String content = choice.message() == null
                ? null
                : choice.message().content();
        long durationMs = Duration.ofNanos(
                System.nanoTime() - startNanos
        ).toMillis();
        ModelUsage usage = toModelUsage(
                deepSeekChatResponse.usage()
        );
        ModelCompletionStatus completionStatus =
                mapCompletionStatus(finishReason);

        if (completionStatus != ModelCompletionStatus.COMPLETED) {
            ModelCompletionException exception =
                    new ModelCompletionException(
                            completionStatus,
                            finishReason,
                            deepSeekChatResponse.id(),
                            deepSeekChatResponse.model(),
                            usage,
                            durationMs,
                            content
                    );

            log.warn(
                    "DeepSeek调用未完整完成，providerRequestId={}, model={}, completionStatus={}, finishReason={}, durationMs={}, outputChars={}, outputSha256={}, totalTokens={}",
                    exception.providerRequestId(),
                    exception.model(),
                    exception.completionStatus(),
                    exception.providerFinishReason(),
                    exception.durationMs(),
                    exception.outputChars(),
                    exception.outputSha256(),
                    usage == null ? null : usage.totalTokens()
            );

            throw exception;
        }

        if (content == null || content.isBlank()) {
            throw new ModelException(
                    ModelErrorType.INVALID_RESPONSE,
                    "大模型返回消息为空"
            );
        }

        log.info(
                "DeepSeek调用完成，requestId={}, model={}, durationMs={}, totalTokens={}",
                deepSeekChatResponse.id(),
                deepSeekChatResponse.model(),
                durationMs,
                usage == null ? null : usage.totalTokens()
        );

        return deepSeekChatResponse;
    }
    /** 在调用Deadline内等待完整非流式响应。 */
    private HttpResponse<String> sendWithinDeadline(HttpRequest request, Duration timeout)
            throws IOException, InterruptedException {
        CompletableFuture<HttpResponse<String>> responseFuture = httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        try {
            return responseFuture.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            responseFuture.cancel(true);
            HttpTimeoutException timeoutException =
                    new HttpTimeoutException("等待模型供应商完整响应超时");
            timeoutException.initCause(exception);
            throw timeoutException;
        } catch (InterruptedException exception) {
            responseFuture.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            throw unwrapAsyncFailure(exception);
        }
    }

    /** 解包异步传输异常并保留可分类的IO根因。 */
    private IOException unwrapAsyncFailure(ExecutionException exception) {
        Throwable cause = exception.getCause();
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof IOException ioException) return ioException;
        if (cause instanceof RuntimeException runtimeException) throw runtimeException;
        if (cause instanceof Error error) throw error;
        return new IOException("模型异步调用失败", cause);
    }

    /**
     * 执行流式输出
     * @param providerRequest
     * @param chunkConsumer
     * @throws IOException
     * @throws InterruptedException
     */
    private void executeStream(DeepSeekChatRequest providerRequest,
                               Duration timeout,
                               Consumer<DeepSeekStreamChunk> chunkConsumer)
            throws IOException, InterruptedException {
        String jsonBody = serializeRequest(providerRequest);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(properties.getBaseUrl().resolve("/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(
                        jsonBody,
                        StandardCharsets.UTF_8
                ))
                .build();
        HttpResponse<InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream());
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode > 299) {
            response.body().close();
            throw mapHttpError(statusCode);
        }
        sseParser.parse(response.body(), chunkConsumer);
    }


    /**
     * ModelRequest 转 DeepSeek DTO
     * @param request
     * @return
     */
    private DeepSeekChatRequest toProviderRequest(ModelRequest request, boolean stream) {
        if (request == null || request.messages() == null || request.messages().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模型消息不能为空");
        }

        List<DeepSeekChatRequest.Message> messages = request.messages().stream()
                .map(message -> {
                    if (message == null
                            || message.role() == null
                            || message.content() == null
                            || message.content().isBlank()) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "模型消息不合法");
                    }

                    return new DeepSeekChatRequest.Message(
                            message.role().name().toLowerCase(Locale.ROOT),
                            message.content()
                    );
                })
                .toList();
        String responseFormat = switch (request.outputFormat()) {
            case TEXT -> "text";
            case JSON_OBJECT -> "json_object";
        };

        return new DeepSeekChatRequest(
                new DeepSeekChatRequest.ResponseFormat(responseFormat),
                properties.getName(),
                messages,
                request.maxOutputTokens(),
                request.temperature(),
                new DeepSeekChatRequest.Thinking("disabled"),
                stream,
                stream ? new DeepSeekChatRequest.StreamOptions(true) : null
        );
    }

    /**
     * DeepSeek DTO 转统一响应 ModelResponse
     * @param response
     * @return
     */
    private ModelResponse toModelResponse(DeepSeekChatResponse response) {
        return new ModelResponse(
                response.id(),
                response.model(),
                response.choices().getFirst().message().content(),
                toModelUsage(response.usage())
        );
    }

    private ModelUsage toModelUsage(
            DeepSeekChatResponse.Usage providerUsage
    ) {
        return providerUsage == null
                ? null
                : new ModelUsage(
                providerUsage.promptTokens(),
                providerUsage.completionTokens(),
                providerUsage.totalTokens()
        );
    }

    private ModelCompletionStatus mapCompletionStatus(
            String finishReason
    ) {
        if (finishReason == null || finishReason.isBlank()) {
            return ModelCompletionStatus.UNKNOWN_INCOMPLETE;
        }
        return switch (finishReason.trim().toLowerCase(Locale.ROOT)) {
            case "stop" -> ModelCompletionStatus.COMPLETED;
            case "length" ->
                    ModelCompletionStatus.OUTPUT_TOKEN_LIMIT_REACHED;
            case "content_filter" ->
                    ModelCompletionStatus.CONTENT_FILTERED;
            case "tool_calls" ->
                    ModelCompletionStatus.TOOL_CALLS_REQUESTED;
            case "insufficient_system_resource" ->
                    ModelCompletionStatus.PROVIDER_RESOURCE_INTERRUPTED;
            default -> ModelCompletionStatus.UNKNOWN_INCOMPLETE;
        };
    }
    /**
     * 转换方法，将收到的流式 chunk 加入 eventConsumer 中, 并确认finish状态 和 获取 usage
     * @param chunk
     * @param requestId
     * @param eventConsumer
     */
    private void handleStreamChunk(
            DeepSeekStreamChunk chunk,
            String requestId,
            Consumer<ModelStreamEvent> eventConsumer,
            StreamState state
    ) {
        // 校验 chunk，如果存在则写入
        if (chunk == null) return;
        if (chunk.id() != null && !chunk.id().isBlank()) state.providerRequestId = chunk.id();
        if (chunk.model() != null && !chunk.model().isBlank()) state.model = chunk.model();

        // Usage可能出现在choices为空的独立Chunk中，必须先处理
        if (chunk.usage() != null) {
            state.usage = new ModelUsage(
                    chunk.usage().promptTokens(),
                    chunk.usage().completionTokens(),
                    chunk.usage().totalTokens()
            );
        }

        if (chunk.choices() == null
                || chunk.choices().isEmpty()
                || chunk.choices().getFirst() == null) {
            return;
        }

        DeepSeekStreamChunk.Choice choice = chunk.choices().getFirst();

        if (choice.finishReason() != null) {
            state.finishReason = choice.finishReason();
        }

        if (choice.delta() == null) return;

        String content = choice.delta().content();

        if (content == null || content.isEmpty()) {
            return;
        }

        state.output.append(content);

        eventConsumer.accept(new ModelStreamEvent(
                ModelStreamEventType.DELTA,
                requestId,
                content,
                null,
                null
        ));
    }

    /**
     * 处理ERROR 信息 并加入 eventConsumer
     * @param requestId
     * @param message
     * @param eventConsumer
     */
    private void emitErrorEvent(
            String requestId,
            ModelErrorType errorType,
            String message,
            Consumer<ModelStreamEvent> eventConsumer
    ) {
        eventConsumer.accept(new ModelStreamEvent(
                ModelStreamEventType.ERROR,
                requestId,
                message,
                null,
                errorType
        ));
    }

    /**
     * 处理大模型典型错误
     * @param requestId
     * @param exception
     * @param eventConsumer
     */
    private void emitClassifiedError(
            String requestId,
            ModelException exception,
            Consumer<ModelStreamEvent> eventConsumer,
            StreamState state
    ) {
        long durationMs = Duration.ofNanos(
                System.nanoTime() - state.startNanos
        ).toMillis();

        if (exception instanceof ModelCompletionException completionException) {
            Long totalTokens = completionException.usage() == null
                    ? null
                    : completionException.usage().totalTokens();
            log.warn(
                    "DeepSeek流式调用未完整完成，requestId={}, providerRequestId={}, model={}, completionStatus={}, finishReason={}, durationMs={}, outputChars={}, outputSha256={}, totalTokens={}",
                    requestId,
                    completionException.providerRequestId(),
                    completionException.model(),
                    completionException.completionStatus(),
                    completionException.providerFinishReason(),
                    completionException.durationMs(),
                    completionException.outputChars(),
                    completionException.outputSha256(),
                    totalTokens
            );
        } else {
            log.error(
                    "DeepSeek流式调用失败，requestId={}, providerRequestId={}, model={}, durationMs={}, errorType={}, message={}",
                    requestId,
                    state.providerRequestId,
                    state.model,
                    durationMs,
                    exception.getErrorType(),
                    exception.getMessage(),
                    exception
            );
        }

        emitErrorEvent(
                requestId,
                exception.getErrorType(),
                exception.getMessage(),
                eventConsumer
        );
    }

    /**
     * 统一模型错误映射方法
     * @param statusCode
     * @return
     */
    private ModelException mapHttpError(int statusCode) {
        return switch (statusCode) {
            case 401 -> new ModelException(ModelErrorType.AUTHENTICATION_ERROR, "模型供应商鉴权失败");
            case 403 -> new ModelException(ModelErrorType.PERMISSION_ERROR, "模型供应商拒绝访问");
            case 404 -> new ModelException(ModelErrorType.MODEL_NOT_FOUND, "指定模型或模型接口不存在");
            case 408, 504 -> new ModelException(ModelErrorType.TIMEOUT, "模型供应商响应超时");
            case 429 -> new ModelException(ModelErrorType.RATE_LIMITED, "模型供应商请求频率受限");
            default -> {
                if (statusCode >= 500) {
                    yield new ModelException(ModelErrorType.PROVIDER_ERROR, "模型供应商服务异常，statusCode=" + statusCode);
                }
                yield new ModelException(ModelErrorType.PROVIDER_ERROR, "模型供应商拒绝请求，statusCode=" + statusCode);
            }
        };
    }

    /**
     * 统一请求序列化
     * @param request
     * @return
     */
    private String serializeRequest(DeepSeekChatRequest request) {
        try {
            return jsonMapper.writeValueAsString(request);
        } catch (JacksonException e) {
            throw new ModelException(ModelErrorType.CONFIGURATION_ERROR, "模型请求序列化失败", e);
        }
    }

    private static final class StreamState {

        private final long startNanos = System.nanoTime();
        private final StringBuilder output = new StringBuilder();
        private String providerRequestId;
        private String model;
        private String finishReason;
        private ModelUsage usage;
    }

}