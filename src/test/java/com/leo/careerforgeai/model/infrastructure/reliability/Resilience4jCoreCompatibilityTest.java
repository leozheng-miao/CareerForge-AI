package com.leo.careerforgeai.model.infrastructure.reliability;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Resilience4j Core在Java 21和当前Spring Boot 4.1.0工程中的编译与运行兼容性
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
class Resilience4jCoreCompatibilityTest {

    @Test
    void shouldExecuteBoundedSynchronousRetry() {
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ZERO)
                .retryOnException(exception -> exception instanceof IllegalStateException)
                .build();
        Retry retry = Retry.of("deepseek-compatibility-retry", retryConfig);
        AtomicInteger attempts = new AtomicInteger();

        String result = retry.executeSupplier(() -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("transient failure");
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void shouldOpenCircuitAfterConfiguredFailureWindow() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindow(
                        2,
                        2,
                        CircuitBreakerConfig.SlidingWindowType.COUNT_BASED
                )
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of(
                "deepseek-compatibility-circuit-breaker",
                circuitBreakerConfig
        );
        Supplier<String> protectedCall = CircuitBreaker.decorateSupplier(
                circuitBreaker,
                () -> {
                    throw new IllegalStateException("provider failure");
                }
        );

        assertThatThrownBy(protectedCall::get).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(protectedCall::get).isInstanceOf(IllegalStateException.class);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(protectedCall::get).isInstanceOf(CallNotPermittedException.class);
    }
}