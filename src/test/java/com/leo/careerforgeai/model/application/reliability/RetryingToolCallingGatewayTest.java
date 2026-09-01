package com.leo.careerforgeai.model.application.reliability;

import com.leo.careerforgeai.model.application.ToolCallingGateway;
import com.leo.careerforgeai.model.config.ModelReliabilityProperties;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.toolcalling.FinalAnswerResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingRequest;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingTextMessage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolChoiceMode;
import com.leo.careerforgeai.model.domain.toolcalling.ToolDefinition;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Tool Calling瞬时故障有限重试、永久错误直返和总超时约束
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
class RetryingToolCallingGatewayTest {

    private static final FinalAnswerResult SUCCESS = new FinalAnswerResult(
            "request-1",
            "deepseek-v4-flash",
            "{\"answer\":\"ok\"}",
            new ModelUsage(10, 5, 15)
    );

    @ParameterizedTest
    @EnumSource(
            value = ModelErrorType.class,
            names = {"NETWORK_ERROR", "PROVIDER_UNAVAILABLE"}
    )
    void shouldRetryTransientFailureAndReduceAttemptTimeout(
            ModelErrorType transientErrorType
    ) {
        AtomicInteger attempts = new AtomicInteger();
        List<Duration> attemptTimeouts = new ArrayList<>();
        ToolCallingGateway delegate = request -> {
            attemptTimeouts.add(request.timeout());
            if (attempts.incrementAndGet() == 1) {
                throw new ModelException(
                        transientErrorType,
                        "供应商暂时不可用"
                );
            }
            return SUCCESS;
        };
        ModelReliabilityMetrics metrics = new ModelReliabilityMetrics();
        RetryingToolCallingGateway gateway =
                new RetryingToolCallingGateway(
                        delegate,
                        properties(
                                3,
                                Duration.ofMillis(1),
                                Duration.ofMillis(4),
                                Duration.ofMillis(10)
                        ),
                        metrics
                );

        assertThat(gateway.call(request(Duration.ofSeconds(1))))
                .isSameAs(SUCCESS);
        assertThat(attempts).hasValue(2);
        assertThat(attemptTimeouts.get(1))
                .isLessThan(attemptTimeouts.get(0));
        assertThat(metrics.snapshot()).isEqualTo(
                new ModelReliabilityMetricsSnapshot(
                        1,
                        1,
                        1,
                        0,
                        0
                )
        );
    }
    @Test
    void shouldKeepLastTransientFailureAfterMaximumAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        ModelException failure = new ModelException(
                ModelErrorType.PROVIDER_ERROR,
                "供应商服务异常"
        );
        ToolCallingGateway delegate = request -> {
            attempts.incrementAndGet();
            throw failure;
        };
        RetryingToolCallingGateway gateway = new RetryingToolCallingGateway(
                delegate,
                properties(3, Duration.ofMillis(1), Duration.ofMillis(2), Duration.ofMillis(10)),
                new ModelReliabilityMetrics()
        );

        assertThatThrownBy(() -> gateway.call(request(Duration.ofSeconds(1))))
                .isSameAs(failure);
        assertThat(attempts).hasValue(3);
    }

    @ParameterizedTest
    @EnumSource(
            value = ModelErrorType.class,
            names = {
                    "CONFIGURATION_ERROR",
                    "AUTHENTICATION_ERROR",
                    "PERMISSION_ERROR",
                    "MODEL_NOT_FOUND",
                    "CAPACITY_REJECTED",
                    "PROVIDER_INCOMPLETE",
                    "PROVIDER_REQUEST_REJECTED",
                    "INVALID_RESPONSE",
                    "STRUCTURED_OUTPUT_INVALID"
            }
    )
    void shouldNotRetryPermanentFailure(ModelErrorType errorType) {
        AtomicInteger attempts = new AtomicInteger();
        ModelException failure = new ModelException(errorType, "不可重试错误");
        ToolCallingGateway delegate = request -> {
            attempts.incrementAndGet();
            throw failure;
        };
        RetryingToolCallingGateway gateway = new RetryingToolCallingGateway(
                delegate,
                properties(3, Duration.ofMillis(1), Duration.ofMillis(2), Duration.ofMillis(10)),
                new ModelReliabilityMetrics()
        );

        assertThatThrownBy(() -> gateway.call(request(Duration.ofSeconds(1))))
                .isSameAs(failure);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void shouldStopWhenRetryDelayExceedsTotalTimeout() {
        AtomicInteger attempts = new AtomicInteger();
        ToolCallingGateway delegate = request -> {
            attempts.incrementAndGet();
            throw new ModelException(ModelErrorType.NETWORK_ERROR, "连接失败");
        };
        RetryingToolCallingGateway gateway = new RetryingToolCallingGateway(
                delegate,
                properties(3, Duration.ofMillis(100), Duration.ofMillis(100), Duration.ofMillis(100)),
                new ModelReliabilityMetrics()
        );

        assertThatThrownBy(() -> gateway.call(request(Duration.ofMillis(20))))
                .isInstanceOfSatisfying(ModelException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ModelErrorType.TIMEOUT)
                );
        assertThat(attempts).hasValue(1);
    }

    @Test
    void shouldNotRetryWhenRetryAfterExceedsConfiguredMaximum() {
        AtomicInteger attempts = new AtomicInteger();
        ModelException failure = new ModelException(
                ModelErrorType.RATE_LIMITED,
                "供应商限流",
                Duration.ofSeconds(1)
        );
        ToolCallingGateway delegate = request -> {
            attempts.incrementAndGet();
            throw failure;
        };
        RetryingToolCallingGateway gateway = new RetryingToolCallingGateway(
                delegate,
                properties(3, Duration.ofMillis(1), Duration.ofMillis(2), Duration.ofMillis(10)),
                new ModelReliabilityMetrics()
        );

        assertThatThrownBy(() -> gateway.call(request(Duration.ofSeconds(2))))
                .isSameAs(failure);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void shouldStopBeforeDelegateWhenThreadIsAlreadyInterrupted() {
        AtomicInteger attempts = new AtomicInteger();
        ToolCallingGateway delegate = request -> {
            attempts.incrementAndGet();
            return SUCCESS;
        };
        ModelReliabilityMetrics metrics = new ModelReliabilityMetrics();
        RetryingToolCallingGateway gateway = new RetryingToolCallingGateway(
                delegate,
                properties(3, Duration.ofMillis(1), Duration.ofMillis(2), Duration.ofMillis(10)),
                metrics
        );

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> gateway.call(request(Duration.ofSeconds(1))))
                    .isInstanceOfSatisfying(ModelException.class, exception ->
                            assertThat(exception.getErrorType()).isEqualTo(ModelErrorType.NETWORK_ERROR)
                    );
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(attempts).hasValue(0);
            assertThat(metrics.snapshot().logicalCalls()).isZero();
        } finally {
            Thread.interrupted();
        }
    }

    private ToolCallingRequest request(Duration timeout) {
        return new ToolCallingRequest(
                List.of(
                        new ToolCallingTextMessage(ModelRole.SYSTEM, "system"),
                        new ToolCallingTextMessage(ModelRole.USER, "message")
                ),
                List.of(new ToolDefinition("search", "搜索材料", "{\"type\":\"object\"}")),
                ToolChoiceMode.AUTO,
                ModelOutputFormat.JSON_OBJECT,
                128,
                timeout
        );
    }

    private ModelReliabilityProperties properties(
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff,
            Duration maxRetryAfter
    ) {
        return new ModelReliabilityProperties(
                maxAttempts,
                initialBackoff,
                maxBackoff,
                2.0,
                maxRetryAfter,
                10,
                5,
                50.0F,
                Duration.ofSeconds(15),
                2
        );
    }
}