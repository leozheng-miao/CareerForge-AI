package com.leo.careerforgeai.model.application.reliability;

import com.leo.careerforgeai.model.config.ModelReliabilityProperties;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @program: CareerForge-AI
 * @description: 为工具调用和面试角色调用复用受Deadline约束的模型有限重试
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Slf4j
public class ModelRetryExecutor {

    private static final String RETRY_NAME = "deepseek-model-call";

    private final ModelReliabilityProperties properties;
    private final ModelReliabilityMetrics metrics;

    public ModelRetryExecutor(ModelReliabilityProperties properties, ModelReliabilityMetrics metrics) {
        this.properties = Objects.requireNonNull(properties, "properties不能为空");
        this.metrics = Objects.requireNonNull(metrics, "metrics不能为空");
    }

    public <T> T execute(Duration timeout, ModelCall<T> modelCall) {
        Objects.requireNonNull(timeout, "timeout不能为空");
        Objects.requireNonNull(modelCall, "modelCall不能为空");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout必须大于0");
        if (Thread.currentThread().isInterrupted()) {
            throw new ModelException(ModelErrorType.NETWORK_ERROR, "模型调用线程已中断");
        }

        metrics.recordLogicalCall();
        AtomicInteger attempts = new AtomicInteger();
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        Retry retry = Retry.of(RETRY_NAME, retryConfig(deadlineNanos));
        retry.getEventPublisher().onRetry(event -> log.warn(
                "模型瞬时故障，执行有限重试，attempt={}, waitMs={}, errorType={}",
                event.getNumberOfRetryAttempts(), event.getWaitInterval().toMillis(),
                errorType(event.getLastThrowable())
        ));

        try {
            T result = retry.executeSupplier(() -> {
                int attempt = attempts.incrementAndGet();
                if (attempt > 1) metrics.recordRetryAttempt();
                return modelCall.call(remainingTimeout(deadlineNanos));
            });
            if (attempts.get() > 1) metrics.recordSucceededAfterRetry();
            return result;
        } catch (RuntimeException exception) {
            if (attempts.get() > 1) metrics.recordFailedAfterRetry();
            throw exception;
        }
    }

    private RetryConfig retryConfig(long deadlineNanos) {
        return RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .retryOnException(this::isRetryable)
                .intervalBiFunction((attempt, outcome) -> retryDelayMillis(
                        attempt, outcome.isLeft() ? outcome.getLeft() : null, deadlineNanos))
                .build();
    }

    private boolean isRetryable(Throwable failure) {
        if (Thread.currentThread().isInterrupted()) return false;
        if (!(failure instanceof ModelException exception)) return false;
        if (exception.getRetryAfter() != null
                && exception.getRetryAfter().compareTo(properties.maxRetryAfter()) > 0) {
            return false;
        }
        return switch (exception.getErrorType()) {
            case RATE_LIMITED,
                 TIMEOUT,
                 NETWORK_ERROR,
                 PROVIDER_ERROR,
                 PROVIDER_UNAVAILABLE -> true;
            default -> false;
        };
    }

    private long retryDelayMillis(int failedAttempt, Throwable failure, long deadlineNanos) {
        Duration delay = localBackoff(failedAttempt);
        if (failure instanceof ModelException exception && exception.getRetryAfter() != null
                && exception.getRetryAfter().compareTo(delay) > 0) {
            delay = exception.getRetryAfter();
        }
        Duration remaining = remainingTimeout(deadlineNanos);
        if (delay.compareTo(remaining) >= 0) {
            throw new ModelException(ModelErrorType.TIMEOUT, "模型重试等待将超过本次调用总超时", failure);
        }
        return delay.toMillis();
    }

    private Duration localBackoff(int failedAttempt) {
        long initialNanos = properties.initialBackoff().toNanos();
        long maximumNanos = properties.maxBackoff().toNanos();
        double calculatedNanos = initialNanos * Math.pow(properties.backoffMultiplier(), failedAttempt - 1);
        return Duration.ofNanos(calculatedNanos >= maximumNanos
                ? maximumNanos : (long) Math.ceil(calculatedNanos));
    }

    private Duration remainingTimeout(long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) throw new ModelException(ModelErrorType.TIMEOUT, "模型调用总超时已耗尽");
        return Duration.ofNanos(remainingNanos);
    }

    private ModelErrorType errorType(Throwable failure) {
        return failure instanceof ModelException exception
                ? exception.getErrorType() : ModelErrorType.PROVIDER_ERROR;
    }

    /**
     * @program: CareerForge-AI
     * @description: 接收当前剩余Deadline并执行一次模型HTTP尝试
     * @author: Miao Zheng
     * @date: 2026-08-27
     * @param <T> 单次模型调用结果类型
     **/
    @FunctionalInterface
    public interface ModelCall<T> {

        T call(Duration remainingTimeout);
    }
}