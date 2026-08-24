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
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证模型熔断器打开、拒绝、半开恢复和故障分类
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
class CircuitBreakingToolCallingGatewayTest {

    private static final FinalAnswerResult SUCCESS = new FinalAnswerResult(
            "request-1",
            "deepseek-v4-flash",
            "{\"answer\":\"ok\"}",
            new ModelUsage(10, 5, 15)
    );

    @Test
    void shouldOpenBlockAndCloseAfterSuccessfulHalfOpenProbes() {
        AdjustableClock clock = new AdjustableClock(
                Instant.parse("2026-08-24T00:00:00Z")
        );
        ModelReliabilityMetrics metrics = new ModelReliabilityMetrics();
        ModelCircuitBreaker circuitBreaker = new ModelCircuitBreaker(
                properties(),
                clock,
                metrics
        );
        AtomicInteger attempts = new AtomicInteger();
        AtomicBoolean failing = new AtomicBoolean(true);
        ToolCallingGateway delegate = request -> {
            attempts.incrementAndGet();
            if (failing.get()) {
                throw new ModelException(
                        ModelErrorType.PROVIDER_ERROR,
                        "供应商服务异常"
                );
            }
            return SUCCESS;
        };
        CircuitBreakingToolCallingGateway gateway =
                new CircuitBreakingToolCallingGateway(delegate, circuitBreaker);

        assertThatThrownBy(() -> gateway.call(request()))
                .isInstanceOf(ModelException.class);
        assertThatThrownBy(() -> gateway.call(request()))
                .isInstanceOf(ModelException.class);
        assertThat(circuitBreaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> gateway.call(request()))
                .isInstanceOfSatisfying(ModelException.class, exception ->
                        assertThat(exception.getErrorType())
                                .isEqualTo(ModelErrorType.CIRCUIT_OPEN)
                );
        assertThat(attempts).hasValue(2);

        failing.set(false);
        clock.advance(Duration.ofSeconds(6));

        assertThat(gateway.call(request())).isSameAs(SUCCESS);
        assertThat(circuitBreaker.state())
                .isEqualTo(CircuitBreaker.State.HALF_OPEN);

        assertThat(gateway.call(request())).isSameAs(SUCCESS);
        assertThat(circuitBreaker.state())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(attempts).hasValue(4);
        assertThat(metrics.snapshot().circuitRejectedCalls()).isEqualTo(1);
    }

    @Test
    void shouldIgnorePermanentErrorsButRecordInvalidModelResponses() {
        AdjustableClock clock = new AdjustableClock(
                Instant.parse("2026-08-24T00:00:00Z")
        );
        ModelCircuitBreaker circuitBreaker = new ModelCircuitBreaker(
                properties(),
                clock,
                new ModelReliabilityMetrics()
        );
        AtomicReference<ModelErrorType> errorType = new AtomicReference<>(
                ModelErrorType.AUTHENTICATION_ERROR
        );
        ToolCallingGateway delegate = request -> {
            throw new ModelException(errorType.get(), "模型调用失败");
        };
        CircuitBreakingToolCallingGateway gateway =
                new CircuitBreakingToolCallingGateway(delegate, circuitBreaker);

        assertThatThrownBy(() -> gateway.call(request()))
                .isInstanceOf(ModelException.class);
        assertThatThrownBy(() -> gateway.call(request()))
                .isInstanceOf(ModelException.class);
        assertThat(circuitBreaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);

        errorType.set(ModelErrorType.INVALID_RESPONSE);

        assertThatThrownBy(() -> gateway.call(request()))
                .isInstanceOf(ModelException.class);
        assertThatThrownBy(() -> gateway.call(request()))
                .isInstanceOf(ModelException.class);
        assertThat(circuitBreaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    private ToolCallingRequest request() {
        return new ToolCallingRequest(
                List.of(
                        new ToolCallingTextMessage(ModelRole.SYSTEM, "system"),
                        new ToolCallingTextMessage(ModelRole.USER, "message")
                ),
                List.of(new ToolDefinition(
                        "search",
                        "搜索材料",
                        "{\"type\":\"object\"}"
                )),
                ToolChoiceMode.AUTO,
                ModelOutputFormat.JSON_OBJECT,
                128,
                Duration.ofSeconds(1)
        );
    }

    private ModelReliabilityProperties properties() {
        return new ModelReliabilityProperties(
                1,
                Duration.ofMillis(1),
                Duration.ofMillis(1),
                2.0,
                Duration.ofMillis(10),
                2,
                2,
                50.0F,
                Duration.ofSeconds(5),
                2
        );
    }

    /**
     * @program: CareerForge-AI
     * @description: 为熔断状态测试提供无需sleep的可推进时钟
     * @author: Miao Zheng
     * @date: 2026-08-24
     */
    private static final class AdjustableClock extends Clock {

        private final AtomicReference<Instant> current;
        private final ZoneId zone;

        private AdjustableClock(Instant initial) {
            this(initial, ZoneOffset.UTC);
        }

        private AdjustableClock(Instant initial, ZoneId zone) {
            this.current = new AtomicReference<>(initial);
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return zone.equals(this.zone)
                    ? this
                    : Clock.fixed(current.get(), zone);
        }

        @Override
        public Instant instant() {
            return current.get();
        }

        private void advance(Duration duration) {
            current.updateAndGet(instant -> instant.plus(duration));
        }
    }
}