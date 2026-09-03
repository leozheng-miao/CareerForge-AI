package com.leo.careerforgeai.model.application.routing;

import com.leo.careerforgeai.model.application.ProviderToolCallingClient;
import com.leo.careerforgeai.model.application.ToolCallingGateway;
import com.leo.careerforgeai.model.application.audit.ModelCallAuditRepository;
import com.leo.careerforgeai.model.application.reliability.ModelCallBulkhead;
import com.leo.careerforgeai.model.application.reliability.ModelCircuitBreaker;
import com.leo.careerforgeai.model.application.reliability.ModelReliabilityMetrics;
import com.leo.careerforgeai.model.application.reliability.ModelRetryExecutor;
import com.leo.careerforgeai.model.config.ModelReliabilityProperties;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.audit.ModelCallAudit;
import com.leo.careerforgeai.model.domain.routing.*;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingModelResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingRequest;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.model.exception.completion.ModelCompletionException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/**
 * @program: CareerForge-AI
 * @description: 为Career Coach执行服务端Tool Calling路由、可靠性保护、Tracing和耐久审计。
 * @author: Miao Zheng
 * @date: 2026-09-03
 */
@Component
@Primary
@Profile("!performance-stub")
@Slf4j
public final class RoutingToolCallingGateway implements ToolCallingGateway {
    private static final ModelTaskType TASK_TYPE = ModelTaskType.CAREER_COACH;
    private final TaskAwareModelRouter router;
    private final Map<String, ProviderToolCallingClient> clients;
    private final ModelCircuitBreaker circuitBreaker;
    private final ModelCallBulkhead bulkhead;
    private final ModelRetryExecutor retryExecutor;
    private final ObservationRegistry observationRegistry;
    private final ModelCallAuditRepository auditRepository;

    public RoutingToolCallingGateway(TaskAwareModelRouter router, List<ProviderToolCallingClient> clients,
                                     ModelCircuitBreaker circuitBreaker, ModelCallBulkhead bulkhead,
                                     ModelReliabilityProperties reliabilityProperties,
                                     ModelReliabilityMetrics reliabilityMetrics,
                                     ObservationRegistry observationRegistry,
                                     ModelCallAuditRepository auditRepository) {
        this.router = Objects.requireNonNull(router, "router不能为空");
        if (clients == null || clients.isEmpty()) throw new IllegalArgumentException("clients不能为空");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker不能为空");
        this.bulkhead = Objects.requireNonNull(bulkhead, "bulkhead不能为空");
        this.retryExecutor = new ModelRetryExecutor(reliabilityProperties, reliabilityMetrics);
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "observationRegistry不能为空");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository不能为空");
        Map<String, ProviderToolCallingClient> registry = new LinkedHashMap<>();
        for (ProviderToolCallingClient client : clients) {
            String providerId = requireProviderId(client.providerId());
            if (registry.putIfAbsent(providerId, client) != null) {
                throw new IllegalArgumentException("供应商Tool Calling Client重复: " + providerId);
            }
        }
        this.clients = Map.copyOf(registry);
    }

    @Override
    public ToolCallingModelResult call(ToolCallingRequest request) {
        Objects.requireNonNull(request, "request不能为空");
        Set<ModelCapability> required = requiredCapabilities(request);
        ModelRouteDecision decision = router.route(TASK_TYPE, required, request.maxOutputTokens(),
                request.maxOutputTokens(), request.timeout(), true);
        List<ModelExecutionProfile> attempts = new ArrayList<>();
        attempts.add(decision.selectedProfile());
        attempts.addAll(decision.fallbackProfiles());
        long startedNanos = System.nanoTime();

        for (int index = 0; index < attempts.size(); index++) {
            ModelExecutionProfile profile = attempts.get(index);
            try {
                return execute(profile, decision.routingVersion(), request, startedNanos, index > 0);
            } catch (ModelException exception) {
                boolean lastAttempt = index == attempts.size() - 1;
                if (lastAttempt || !isFallbackEligible(exception)) throw exception;
                log.warn("Tool Calling切换Fallback，taskType={}, failedProfile={}, errorType={}, nextProfile={}",
                        TASK_TYPE, profile.profileId(), exception.getErrorType(),
                        attempts.get(index + 1).profileId());
            }
        }
        throw new ModelException(ModelErrorType.PROVIDER_UNAVAILABLE, "Tool Calling路由没有可执行Profile");
    }

    private ToolCallingModelResult execute(ModelExecutionProfile profile, String routingVersion,
                                           ToolCallingRequest request, long startedNanos,
                                           boolean fallback) {
        ToolCallingRequest attemptRequest = effectiveRequest(request, profile, startedNanos);
        return circuitBreaker.execute(profile.provider(), () ->
                retryExecutor.execute(attemptRequest.timeout(), remaining ->
                        bulkhead.execute(profile.provider(), () -> observePhysicalCall(profile,
                                routingVersion, fallback,
                                () -> client(profile).call(profile, attemptRequest.withTimeout(remaining))))));
    }

    private ToolCallingModelResult observePhysicalCall(ModelExecutionProfile profile,
                                                       String routingVersion, boolean fallback,
                                                       Supplier<ToolCallingModelResult> action) {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        Observation observation = Observation.createNotStarted("careerforge.model.call", observationRegistry)
                .contextualName("model tool_calling")
                .lowCardinalityKeyValue("task.type", tag(TASK_TYPE))
                .lowCardinalityKeyValue("provider", profile.provider())
                .lowCardinalityKeyValue("model.profile", profile.profileId())
                .lowCardinalityKeyValue("reasoning.mode", tag(profile.reasoningMode()))
                .lowCardinalityKeyValue("operation", "tool_calling")
                .lowCardinalityKeyValue("fallback", Boolean.toString(fallback))
                .start();
        ToolCallingModelResult result = null;
        RuntimeException failure = null;
        String traceId = null;
        String spanId = null;

        try (Observation.Scope ignored = observation.openScope()) {
            traceId = MDC.get("traceId");
            spanId = MDC.get("spanId");
            result = action.get();
            observation.lowCardinalityKeyValue("outcome", "success")
                    .lowCardinalityKeyValue("error.category", "none");
            return result;
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
            recordAudit(profile, routingVersion, fallback, startedAt, startedNanos,
                    result, failure, traceId, spanId);
        }
    }

    private void recordAudit(ModelExecutionProfile profile, String routingVersion, boolean fallback,
                             Instant startedAt, long startedNanos, ToolCallingModelResult result,
                             RuntimeException failure, String traceId, String spanId) {
        try {
            ModelCompletionException completion =
                    failure instanceof ModelCompletionException value ? value : null;
            boolean success = result != null;
            auditRepository.save(new ModelCallAudit(
                    UUID.randomUUID(), startedAt, TASK_TYPE, ModelCallAudit.OperationType.TOOL_CALLING,
                    profile.provider(), profile.profileId(),
                    success ? result.model() : completion == null ? profile.model() : completion.model(),
                    routingVersion, profile.priceVersion(), profile.reasoningMode(), fallback,
                    success ? ModelCallAudit.Outcome.SUCCESS : ModelCallAudit.Outcome.FAILURE,
                    success ? null : failure instanceof ModelException modelException
                            ? modelException.getErrorType().name() : "UNEXPECTED",
                    success ? result.requestId() : completion == null
                            ? null : completion.providerRequestId(),
                    success ? result.usage() : completion == null ? null : completion.usage(),
                    Duration.ofNanos(Math.max(0, System.nanoTime() - startedNanos)).toMillis(),
                    traceId, spanId
            ));
        } catch (RuntimeException auditFailure) {
            log.warn("Tool Calling审计写入失败，provider={}, profile={}, exceptionType={}",
                    profile.provider(), profile.profileId(),
                    auditFailure.getClass().getSimpleName());
        }
    }

    private ProviderToolCallingClient client(ModelExecutionProfile profile) {
        ProviderToolCallingClient client = clients.get(profile.provider());
        if (client == null) {
            throw new ModelException(ModelErrorType.CONFIGURATION_ERROR,
                    "未注册供应商Tool Calling Client，provider=" + profile.provider());
        }
        return client;
    }

    private static ToolCallingRequest effectiveRequest(ToolCallingRequest request,
                                                       ModelExecutionProfile profile,
                                                       long startedNanos) {
        Duration remaining = request.timeout().minusNanos(
                Math.max(0, System.nanoTime() - startedNanos));
        if (remaining.isZero() || remaining.isNegative()) {
            throw new ModelException(ModelErrorType.TIMEOUT, "Tool Calling总Deadline已耗尽");
        }
        Duration timeout = profile.timeout().compareTo(remaining) < 0
                ? profile.timeout() : remaining;
        return request.withTimeout(timeout);
    }

    private static Set<ModelCapability> requiredCapabilities(ToolCallingRequest request) {
        EnumSet<ModelCapability> required =
                EnumSet.of(ModelCapability.CHAT, ModelCapability.TOOL_CALLING);
        if (request.outputFormat() == ModelOutputFormat.JSON_OBJECT) {
            required.add(ModelCapability.JSON_OBJECT);
        }
        return Set.copyOf(required);
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

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}