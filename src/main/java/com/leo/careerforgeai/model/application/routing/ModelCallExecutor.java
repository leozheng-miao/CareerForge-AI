package com.leo.careerforgeai.model.application.routing;

import com.leo.careerforgeai.model.application.ProviderModelClient;
import com.leo.careerforgeai.model.application.audit.ModelCallAuditRepository;
import com.leo.careerforgeai.model.application.reliability.ModelCallBulkhead;
import com.leo.careerforgeai.model.application.reliability.ModelCircuitBreaker;
import com.leo.careerforgeai.model.application.reliability.ModelReliabilityMetrics;
import com.leo.careerforgeai.model.application.reliability.ModelRetryExecutor;
import com.leo.careerforgeai.model.config.ModelReliabilityProperties;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.audit.ModelCallAudit;
import com.leo.careerforgeai.model.domain.routing.*;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEvent;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEventType;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.model.exception.completion.ModelCompletionException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @program: CareerForge-AI
 * @description: 在共享Deadline内执行后端模型路由、供应商可靠性保护和受限Fallback。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
@Component
@Slf4j
public final class ModelCallExecutor {

    private final TaskAwareModelRouter router;
    private final Map<String, ProviderModelClient> clients;
    private final ModelCircuitBreaker circuitBreaker;
    private final ModelCallBulkhead bulkhead;
    private final ModelRetryExecutor retryExecutor;
    private final ObservationRegistry observationRegistry;
    private final ModelCallAuditRepository auditRepository;

    public ModelCallExecutor(TaskAwareModelRouter router, List<ProviderModelClient> clients,
                             ModelCircuitBreaker circuitBreaker, ModelCallBulkhead bulkhead,
                             ModelReliabilityProperties reliabilityProperties,
                             ModelReliabilityMetrics reliabilityMetrics,
                             ObservationRegistry observationRegistry,
                             ModelCallAuditRepository auditRepository) {
        if (router == null) throw new IllegalArgumentException("router不能为空");
        if (clients == null || clients.isEmpty()) throw new IllegalArgumentException("clients不能为空");
        this.router = router;
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker不能为空");
        this.bulkhead = Objects.requireNonNull(bulkhead, "bulkhead不能为空");
        this.retryExecutor = new ModelRetryExecutor(reliabilityProperties, reliabilityMetrics);
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "observationRegistry不能为空");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository不能为空");
        Map<String, ProviderModelClient> registry = new LinkedHashMap<>();
        for (ProviderModelClient client : clients) {
            if (client == null) throw new IllegalArgumentException("clients不能包含null");
            String providerId = requireProviderId(client.providerId());
            if (registry.putIfAbsent(providerId, client) != null) {
                throw new IllegalArgumentException("供应商Client重复: " + providerId);
            }
        }
        this.clients = Map.copyOf(registry);
    }

    public ModelResponse chat(ModelTaskType taskType, Set<ModelCapability> extraCapabilities,
                              ModelRequest request, int remainingTokenBudget,
                              boolean fallbackAllowed) {
        validateRequest(request, extraCapabilities);
        Set<ModelCapability> required = requiredCapabilities(request, extraCapabilities, false);
        ModelRouteDecision decision = router.route(taskType, required, request.maxOutputTokens(),
                remainingTokenBudget, request.timeout(), fallbackAllowed);
        List<ModelExecutionProfile> attempts = new ArrayList<>();
        attempts.add(decision.selectedProfile());
        attempts.addAll(decision.fallbackProfiles());

        long startedNanos = System.nanoTime();
        for (int index = 0; index < attempts.size(); index++) {
            ModelExecutionProfile profile = attempts.get(index);
            try {
                return executeChat(taskType, profile, decision.routingVersion(), request, startedNanos, index > 0);
            } catch (ModelException exception) {
                boolean lastAttempt = index == attempts.size() - 1;
                if (lastAttempt || !isFallbackEligible(exception)) throw exception;
                log.warn("模型调用切换Fallback，taskType={}, failedProfile={}, errorType={}, nextProfile={}",
                        taskType, profile.profileId(), exception.getErrorType(),
                        attempts.get(index + 1).profileId());
            }
        }
        throw new ModelException(ModelErrorType.PROVIDER_UNAVAILABLE, "模型路由没有可执行Profile");
    }

    public void stream(ModelTaskType taskType, Set<ModelCapability> extraCapabilities,
                       ModelRequest request, int remainingTokenBudget,
                       Consumer<ModelStreamEvent> eventConsumer) {
        validateRequest(request, extraCapabilities);
        if (eventConsumer == null) throw new IllegalArgumentException("eventConsumer不能为空");
        Set<ModelCapability> required = requiredCapabilities(request, extraCapabilities, true);
        ModelRouteDecision decision = router.route(taskType, required, request.maxOutputTokens(),
                remainingTokenBudget, request.timeout(), false);
        ModelExecutionProfile profile = decision.selectedProfile();
        ModelRequest effectiveRequest = effectiveRequest(request, profile, System.nanoTime());
        circuitBreaker.execute(profile.provider(), () -> bulkhead.execute(profile.provider(), () -> {
            observeStreamCall(taskType, profile, decision.routingVersion(), false,
                    effectiveRequest, eventConsumer);
            return null;
        }));
    }

    private ModelResponse executeChat(ModelTaskType taskType, ModelExecutionProfile profile, String routingVersion,
                                      ModelRequest request, long startedNanos, boolean fallback) {
        ModelRequest attemptRequest = effectiveRequest(request, profile, startedNanos);
        return circuitBreaker.execute(profile.provider(), () ->
                retryExecutor.execute(attemptRequest.timeout(), remaining ->
                        bulkhead.execute(profile.provider(), () -> observeChatCall(taskType, profile,
                                routingVersion, fallback,
                                () -> client(profile).chat(profile, attemptRequest.withTimeout(remaining))))));
    }

    private ModelResponse observeChatCall(ModelTaskType taskType, ModelExecutionProfile profile,
                                          String routingVersion, boolean fallback,
                                          Supplier<ModelResponse> action) {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        Observation observation = observation(taskType, profile, fallback, "chat");
        ModelResponse response = null;
        RuntimeException failure = null;
        String traceId = null;
        String spanId = null;
        try (Observation.Scope ignored = observation.openScope()) {
            traceId = MDC.get("traceId");
            spanId = MDC.get("spanId");
            response = action.get();
            observation.lowCardinalityKeyValue("outcome", "success")
                    .lowCardinalityKeyValue("error.category", "none");
            return response;
        } catch (ModelException exception) {
            failure = exception;
            observation.lowCardinalityKeyValue("outcome", "failure")
                    .lowCardinalityKeyValue("error.category", tag(exception.getErrorType()))
                    .error(exception);
            throw exception;
        } catch (RuntimeException exception) {
            failure = exception;
            observation.lowCardinalityKeyValue("outcome", "failure")
                    .lowCardinalityKeyValue("error.category", "unexpected")
                    .error(exception);
            throw exception;
        } finally {
            observation.stop();
            recordAudit(taskType, profile, routingVersion, ModelCallAudit.OperationType.CHAT, fallback,
                    startedAt, startedNanos, response, failure, null, traceId, spanId);
        }
    }

    private void observeStreamCall(ModelTaskType taskType, ModelExecutionProfile profile, String routingVersion,
                                   boolean fallback, ModelRequest request,
                                   Consumer<ModelStreamEvent> eventConsumer) {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        Observation observation = observation(taskType, profile, fallback, "stream");
        AtomicReference<ModelStreamEvent> terminal = new AtomicReference<>();
        RuntimeException failure = null;
        String traceId = null;
        String spanId = null;
        try (Observation.Scope ignored = observation.openScope()) {
            traceId = MDC.get("traceId");
            spanId = MDC.get("spanId");
            client(profile).stream(profile, request, event -> {
                if (event.type() == ModelStreamEventType.COMPLETED || event.type() == ModelStreamEventType.ERROR) {
                    terminal.set(event);
                }
                eventConsumer.accept(event);
            });
            ModelStreamEvent terminalEvent = terminal.get();
            if (terminalEvent != null && terminalEvent.type() == ModelStreamEventType.COMPLETED) {
                observation.lowCardinalityKeyValue("outcome", "success")
                        .lowCardinalityKeyValue("error.category", "none");
            } else {
                String category = terminalEvent == null || terminalEvent.errorType() == null
                        ? "stream_terminal_missing" : tag(terminalEvent.errorType());
                observation.lowCardinalityKeyValue("outcome", "failure")
                        .lowCardinalityKeyValue("error.category", category);
            }
        } catch (ModelException exception) {
            failure = exception;
            observation.lowCardinalityKeyValue("outcome", "failure")
                    .lowCardinalityKeyValue("error.category", tag(exception.getErrorType()))
                    .error(exception);
            throw exception;
        } catch (RuntimeException exception) {
            failure = exception;
            observation.lowCardinalityKeyValue("outcome", "failure")
                    .lowCardinalityKeyValue("error.category", "unexpected")
                    .error(exception);
            throw exception;
        } finally {
            observation.stop();
            recordAudit(taskType, profile, routingVersion, ModelCallAudit.OperationType.STREAM, fallback,
                    startedAt, startedNanos, null, failure, terminal.get(), traceId, spanId);
        }
    }

    private Observation observation(ModelTaskType taskType, ModelExecutionProfile profile,
                                    boolean fallback, String operation) {
        return Observation.createNotStarted("careerforge.model.call", observationRegistry)
                .contextualName("model " + operation)
                .lowCardinalityKeyValue("task.type", tag(taskType))
                .lowCardinalityKeyValue("provider", profile.provider())
                .lowCardinalityKeyValue("model.profile", profile.profileId())
                .lowCardinalityKeyValue("reasoning.mode", tag(profile.reasoningMode()))
                .lowCardinalityKeyValue("operation", operation)
                .lowCardinalityKeyValue("fallback", Boolean.toString(fallback))
                .start();
    }

    private void recordAudit(ModelTaskType taskType, ModelExecutionProfile profile, String routingVersion,
                             ModelCallAudit.OperationType operationType, boolean fallback, Instant startedAt,
                             long startedNanos, ModelResponse response, RuntimeException failure,
                             ModelStreamEvent terminalEvent, String traceId, String spanId) {
        try {
            ModelCompletionException completion = failure instanceof ModelCompletionException value ? value : null;
            boolean streamSuccess = terminalEvent != null && terminalEvent.type() == ModelStreamEventType.COMPLETED;
            boolean success = response != null || operationType == ModelCallAudit.OperationType.STREAM
                    && failure == null && streamSuccess;
            String errorCategory = success ? null : resolveErrorCategory(failure, terminalEvent);
            String requestId = response == null ? completion == null ? null : completion.providerRequestId()
                    : response.requestId();
            String model = response != null ? response.model()
                    : completion == null ? profile.model() : completion.model();
            var usage = response != null ? response.usage() : completion != null ? completion.usage()
                    : terminalEvent == null ? null : terminalEvent.usage();
            auditRepository.save(new ModelCallAudit(UUID.randomUUID(), startedAt, taskType, operationType,
                    profile.provider(), profile.profileId(), model, routingVersion, profile.priceVersion(),
                    profile.reasoningMode(), fallback,
                    success ? ModelCallAudit.Outcome.SUCCESS : ModelCallAudit.Outcome.FAILURE,
                    errorCategory, requestId, usage,
                    Duration.ofNanos(Math.max(0, System.nanoTime() - startedNanos)).toMillis(), traceId, spanId));
        } catch (RuntimeException auditFailure) {
            log.warn("模型调用审计写入失败，taskType={}, provider={}, profile={}, exceptionType={}",
                    taskType, profile.provider(), profile.profileId(), auditFailure.getClass().getSimpleName());
        }
    }

    private static String resolveErrorCategory(RuntimeException failure, ModelStreamEvent terminalEvent) {
        if (failure instanceof ModelException exception) return exception.getErrorType().name();
        if (failure != null) return "UNEXPECTED";
        if (terminalEvent != null && terminalEvent.errorType() != null) return terminalEvent.errorType().name();
        return "STREAM_TERMINAL_MISSING";
    }

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private ProviderModelClient client(ModelExecutionProfile profile) {
        ProviderModelClient client = clients.get(profile.provider());
        if (client == null) {
            throw new ModelException(ModelErrorType.CONFIGURATION_ERROR,
                    "未注册供应商Client，provider=" + profile.provider());
        }
        return client;
    }

    private static ModelRequest effectiveRequest(ModelRequest request,
                                                 ModelExecutionProfile profile,
                                                 long startedNanos) {
        long elapsedNanos = Math.max(0, System.nanoTime() - startedNanos);
        Duration remaining = request.timeout().minusNanos(elapsedNanos);
        if (remaining.isZero() || remaining.isNegative()) {
            throw new ModelException(ModelErrorType.TIMEOUT, "模型调用总Deadline已耗尽");
        }
        Duration timeout = profile.timeout().compareTo(remaining) < 0
                ? profile.timeout() : remaining;
        return request.withTimeout(timeout);
    }

    private static Set<ModelCapability> requiredCapabilities(
            ModelRequest request, Set<ModelCapability> extras, boolean streaming) {
        EnumSet<ModelCapability> required = EnumSet.of(ModelCapability.CHAT);
        required.addAll(extras);
        if (request.outputFormat() == ModelOutputFormat.JSON_OBJECT) {
            required.add(ModelCapability.JSON_OBJECT);
        }
        if (streaming) required.add(ModelCapability.STREAMING);
        return Set.copyOf(required);
    }

    private static void validateRequest(ModelRequest request,
                                        Set<ModelCapability> capabilities) {
        if (request == null) throw new IllegalArgumentException("request不能为空");
        if (capabilities == null
                || capabilities.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("extraCapabilities不能为空且不能包含null");
        }
    }

    private static boolean isFallbackEligible(ModelException exception) {
        return switch (exception.getErrorType()) {
            case RATE_LIMITED, CAPACITY_REJECTED, CIRCUIT_OPEN, TIMEOUT,
                 NETWORK_ERROR, PROVIDER_ERROR, PROVIDER_UNAVAILABLE -> true;
            default -> false;
        };
    }

    private static String requireProviderId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId不能为空");
        }
        String normalized = providerId.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException("providerId格式非法");
        }
        return normalized;
    }
}
