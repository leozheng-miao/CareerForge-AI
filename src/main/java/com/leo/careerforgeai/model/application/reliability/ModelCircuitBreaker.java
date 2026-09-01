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
import java.util.Objects;
import java.util.function.Supplier;

/**
 * @program: CareerForge-AI
 * @description: 统计逻辑模型调用结果并管理共享熔断器的关闭、打开和半开状态
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
@Slf4j
@Component
public class ModelCircuitBreaker {

    private static final String CIRCUIT_BREAKER_NAME = "deepseek-tool-calling";

    private final CircuitBreaker circuitBreaker;
    private final ModelReliabilityMetrics metrics;

    public ModelCircuitBreaker(
            ModelReliabilityProperties properties,
            Clock clock,
            ModelReliabilityMetrics metrics
    ) {
        Objects.requireNonNull(properties, "properties不能为空");
        Objects.requireNonNull(clock, "clock不能为空");
        this.metrics = Objects.requireNonNull(metrics, "metrics不能为空");

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindow(
                        properties.circuitWindowSize(),
                        properties.circuitMinimumCalls(),
                        CircuitBreakerConfig.SlidingWindowType.COUNT_BASED
                )
                .failureRateThreshold(properties.circuitFailureRateThreshold())
                .waitDurationInOpenState(properties.circuitOpenDuration())
                .permittedNumberOfCallsInHalfOpenState(properties.circuitHalfOpenPermittedCalls())
                .clock(clock)
                .recordException(this::isRecordedFailure)
                .ignoreException(failure -> !isRecordedFailure(failure))
                .build();

        this.circuitBreaker = CircuitBreaker.of(CIRCUIT_BREAKER_NAME, config);
        this.circuitBreaker.getEventPublisher().onStateTransition(event -> log.warn(
                "模型熔断器状态变更，from={}, to={}",
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState()
        ));
    }

    public <T> T execute(Supplier<T> action) {
        Objects.requireNonNull(action, "action不能为空");
        try {
            return circuitBreaker.executeSupplier(action);
        } catch (CallNotPermittedException exception) {
            metrics.recordCircuitRejected();
            throw new ModelException(ModelErrorType.CIRCUIT_OPEN, "模型服务熔断器已打开", exception);
        }
    }

    public CircuitBreaker.State state() {
        return circuitBreaker.getState();
    }

    private boolean isRecordedFailure(Throwable failure) {
        if (Thread.currentThread().isInterrupted()) return false;
        if (!(failure instanceof ModelException exception)) return false;

        return switch (exception.getErrorType()) {
            case RATE_LIMITED,
                 TIMEOUT,
                 NETWORK_ERROR,
                 PROVIDER_ERROR,
                 PROVIDER_UNAVAILABLE,
                 INVALID_RESPONSE,
                 STRUCTURED_OUTPUT_INVALID -> true;
            default -> false;
        };
    }
}