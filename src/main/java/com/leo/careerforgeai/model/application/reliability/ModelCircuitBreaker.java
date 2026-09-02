package com.leo.careerforgeai.model.application.reliability;

import com.leo.careerforgeai.model.config.ModelReliabilityProperties;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * @program: CareerForge-AI
 * @description: 按供应商隔离模型调用熔断状态，防止单一供应商故障影响其他供应商。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
@Slf4j
@Component
public class ModelCircuitBreaker {

    private static final String LEGACY_PROVIDER_ID = "deepseek";
    private final CircuitBreakerConfig config;
    private final ModelReliabilityMetrics metrics;
    private final ConcurrentMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    public ModelCircuitBreaker(ModelReliabilityProperties properties, Clock clock,
                               ModelReliabilityMetrics metrics) {
        Objects.requireNonNull(properties, "properties不能为空");
        Objects.requireNonNull(clock, "clock不能为空");
        this.metrics = Objects.requireNonNull(metrics, "metrics不能为空");
        this.config = CircuitBreakerConfig.custom()
                .slidingWindow(properties.circuitWindowSize(), properties.circuitMinimumCalls(),
                        CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .failureRateThreshold(properties.circuitFailureRateThreshold())
                .waitDurationInOpenState(properties.circuitOpenDuration())
                .permittedNumberOfCallsInHalfOpenState(properties.circuitHalfOpenPermittedCalls())
                .clock(clock)
                .recordException(this::isRecordedFailure)
                .ignoreException(failure -> !isRecordedFailure(failure))
                .build();
    }

    public <T> T execute(Supplier<T> action) {
        return execute(LEGACY_PROVIDER_ID, action);
    }

    public <T> T execute(String providerId, Supplier<T> action) {
        Objects.requireNonNull(action, "action不能为空");
        String normalizedProviderId = requireProviderId(providerId);
        try {
            return circuitBreaker(normalizedProviderId).executeSupplier(action);
        } catch (CallNotPermittedException exception) {
            metrics.recordCircuitRejected();
            throw new ModelException(ModelErrorType.CIRCUIT_OPEN,
                    "模型供应商熔断器已打开，provider=" + normalizedProviderId, exception);
        }
    }

    public CircuitBreaker.State state() {
        return state(LEGACY_PROVIDER_ID);
    }

    public CircuitBreaker.State state(String providerId) {
        return circuitBreaker(requireProviderId(providerId)).getState();
    }

    private CircuitBreaker circuitBreaker(String providerId) {
        return circuitBreakers.computeIfAbsent(providerId, this::createCircuitBreaker);
    }

    private CircuitBreaker createCircuitBreaker(String providerId) {
        CircuitBreaker circuitBreaker = CircuitBreaker.of("model-provider-" + providerId, config);
        circuitBreaker.getEventPublisher().onStateTransition(event -> log.warn(
                "模型熔断器状态变更，provider={}, from={}, to={}", providerId,
                event.getStateTransition().getFromState(), event.getStateTransition().getToState()));
        return circuitBreaker;
    }

    private boolean isRecordedFailure(Throwable failure) {
        if (Thread.currentThread().isInterrupted()
                || !(failure instanceof ModelException exception)) return false;
        return switch (exception.getErrorType()) {
            case RATE_LIMITED, TIMEOUT, NETWORK_ERROR, PROVIDER_ERROR,
                 PROVIDER_UNAVAILABLE, INVALID_RESPONSE, STRUCTURED_OUTPUT_INVALID -> true;
            default -> false;
        };
    }

    private static String requireProviderId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId不能为空");
        }
        String normalized = providerId.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 64
                || !normalized.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException("providerId格式非法");
        }
        return normalized;
    }
}