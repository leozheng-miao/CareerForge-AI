package com.leo.careerforgeai.model.domain.routing;

import com.leo.careerforgeai.model.application.ProviderModelClient;
import com.leo.careerforgeai.model.application.reliability.ModelCallBulkhead;
import com.leo.careerforgeai.model.application.reliability.ModelCircuitBreaker;
import com.leo.careerforgeai.model.application.reliability.ModelReliabilityMetrics;
import com.leo.careerforgeai.model.application.routing.ModelCallExecutor;
import com.leo.careerforgeai.model.application.routing.TaskAwareModelRouter;
import com.leo.careerforgeai.model.config.ModelCallBulkheadProperties;
import com.leo.careerforgeai.model.config.ModelReliabilityProperties;
import com.leo.careerforgeai.model.domain.ModelMessage;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.routing.ModelCapability;
import com.leo.careerforgeai.model.domain.routing.ModelExecutionProfile;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import com.leo.careerforgeai.model.domain.routing.ReasoningMode;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import static org.mockito.Mockito.times;

/**
 * @program: CareerForge-AI
 * @description: 验证模型调用执行器的选路、Deadline收缩和安全Fallback边界。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
class ModelCallExecutorTest {

    @Test
    void shouldSelectProviderAndClampAttemptTimeout() {
        ModelExecutionProfile primary = profile("deepseek-primary", "deepseek",
                Duration.ofSeconds(5));
        ProviderModelClient deepseek = client("deepseek");
        ModelResponse expected = response("deepseek-v4-flash");
        when(deepseek.chat(eq(primary), any())).thenReturn(expected);
        ModelCallExecutor executor = executor(List.of(primary), List.of(deepseek));

        ModelResponse actual = executor.chat(ModelTaskType.MEMORY_EXTRACTION, Set.of(),
                request(), 2_000, true);

        ArgumentCaptor<ModelRequest> requestCaptor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(deepseek).chat(eq(primary), requestCaptor.capture());
        assertThat(actual).isEqualTo(expected);
        assertThat(requestCaptor.getValue().timeout()).isPositive()
                .isLessThanOrEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void shouldFallbackOnlyAfterTransientProviderFailure() {
        ModelExecutionProfile primary = profile("deepseek-primary", "deepseek",
                Duration.ofSeconds(10));
        ModelExecutionProfile fallback = profile("kimi-fallback", "kimi",
                Duration.ofSeconds(10));
        ProviderModelClient deepseek = client("deepseek");
        ProviderModelClient kimi = client("kimi");
        when(deepseek.chat(eq(primary), any())).thenThrow(
                new ModelException(ModelErrorType.RATE_LIMITED, "rate limited"));
        when(kimi.chat(eq(fallback), any())).thenReturn(response("kimi-k2.6"));
        ModelCallExecutor executor = executor(List.of(primary, fallback), List.of(deepseek, kimi));

        ModelResponse response = executor.chat(ModelTaskType.MEMORY_EXTRACTION, Set.of(),
                request(), 2_000, true);

        assertThat(response.model()).isEqualTo("kimi-k2.6");
        verify(deepseek).chat(eq(primary), any());
        verify(kimi).chat(eq(fallback), any());
    }

    @Test
    void shouldNotFallbackAfterInvalidModelOutput() {
        ModelExecutionProfile primary = profile("deepseek-primary", "deepseek",
                Duration.ofSeconds(10));
        ModelExecutionProfile fallback = profile("kimi-fallback", "kimi",
                Duration.ofSeconds(10));
        ProviderModelClient deepseek = client("deepseek");
        ProviderModelClient kimi = client("kimi");
        when(deepseek.chat(eq(primary), any())).thenThrow(
                new ModelException(ModelErrorType.INVALID_RESPONSE, "invalid response"));
        ModelCallExecutor executor = executor(List.of(primary, fallback), List.of(deepseek, kimi));

        assertThatThrownBy(() -> executor.chat(ModelTaskType.MEMORY_EXTRACTION, Set.of(),
                request(), 2_000, true))
                .isInstanceOfSatisfying(ModelException.class,
                        exception -> assertThat(exception.getErrorType())
                                .isEqualTo(ModelErrorType.INVALID_RESPONSE));
        verify(kimi, never()).chat(any(), any());
    }

    @Test
    void shouldRetryThenFallbackWithoutPollutingFallbackProviderCircuit() {
        ModelExecutionProfile primary = profile(
                "deepseek-primary", "deepseek", Duration.ofSeconds(10));
        ModelExecutionProfile fallback = profile(
                "kimi-fallback", "kimi", Duration.ofSeconds(10));
        ProviderModelClient deepseek = client("deepseek");
        ProviderModelClient kimi = client("kimi");

        when(deepseek.chat(eq(primary), any())).thenThrow(
                new ModelException(ModelErrorType.NETWORK_ERROR, "network failed"));
        when(kimi.chat(eq(fallback), any())).thenReturn(response("kimi-k2.6"));

        ModelReliabilityProperties properties = new ModelReliabilityProperties(
                2, Duration.ofMillis(1), Duration.ofMillis(1), 1.0,
                Duration.ofSeconds(1), 2, 2, 100.0F,
                Duration.ofSeconds(30), 1
        );
        ModelReliabilityMetrics metrics = new ModelReliabilityMetrics();
        ModelCircuitBreaker circuitBreaker = new ModelCircuitBreaker(
                properties, Clock.systemUTC(), metrics);
        TaskAwareModelRouter router = new TaskAwareModelRouter(
                Map.of(ModelTaskType.MEMORY_EXTRACTION,
                        List.of(primary, fallback)),
                "routing-v1"
        );
        ModelCallExecutor executor = new ModelCallExecutor(
                router,
                List.of(deepseek, kimi),
                circuitBreaker,
                new ModelCallBulkhead(new ModelCallBulkheadProperties(1)),
                properties,
                metrics
        );

        for (int invocation = 0; invocation < 3; invocation++) {
            assertThat(executor.chat(
                    ModelTaskType.MEMORY_EXTRACTION,
                    Set.of(),
                    request(),
                    2_000,
                    true
            ).model()).isEqualTo("kimi-k2.6");
        }

        verify(deepseek, times(4)).chat(eq(primary), any());
        verify(kimi, times(3)).chat(eq(fallback), any());
        assertThat(circuitBreaker.state("deepseek"))
                .isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.state("kimi"))
                .isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(metrics.snapshot().retryAttempts()).isEqualTo(2);
        assertThat(metrics.snapshot().failedAfterRetry()).isEqualTo(2);
        assertThat(metrics.snapshot().circuitRejectedCalls()).isEqualTo(1);
    }

    private static ModelCallExecutor executor(List<ModelExecutionProfile> profiles,
                                              List<ProviderModelClient> clients) {
        ModelReliabilityProperties properties = new ModelReliabilityProperties(
                1, Duration.ofMillis(1), Duration.ofMillis(1), 1.0,
                Duration.ofSeconds(1), 10, 10, 100.0F,
                Duration.ofSeconds(30), 1
        );
        ModelReliabilityMetrics metrics = new ModelReliabilityMetrics();
        TaskAwareModelRouter router = new TaskAwareModelRouter(
                Map.of(ModelTaskType.MEMORY_EXTRACTION, profiles), "routing-v1");
        return new ModelCallExecutor(
                router,
                clients,
                new ModelCircuitBreaker(properties, Clock.systemUTC(), metrics),
                new ModelCallBulkhead(new ModelCallBulkheadProperties(4)),
                properties,
                metrics
        );
    }

    private static ProviderModelClient client(String providerId) {
        ProviderModelClient client = mock(ProviderModelClient.class);
        when(client.providerId()).thenReturn(providerId);
        return client;
    }

    private static ModelExecutionProfile profile(String id, String provider, Duration timeout) {
        return new ModelExecutionProfile(id, provider, provider + "-model",
                Set.of(ModelCapability.CHAT, ModelCapability.JSON_OBJECT,
                        ModelCapability.STREAMING),
                ReasoningMode.DISABLED, null, 2_000, timeout,
                provider + "-2026-09-02", true);
    }

    private static ModelRequest request() {
        return new ModelRequest(
                List.of(new ModelMessage(ModelRole.USER, "提取稳定结构")),
                ModelOutputFormat.JSON_OBJECT, 1_000, 0.2,
                Duration.ofSeconds(20));
    }

    private static ModelResponse response(String model) {
        return new ModelResponse("request-1", model, "{}", new ModelUsage(10, 2, 12));
    }
}