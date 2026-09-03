package com.leo.careerforgeai.model.evaluation;

import com.leo.careerforgeai.model.application.ProviderModelClient;
import com.leo.careerforgeai.model.application.reliability.*;
import com.leo.careerforgeai.model.application.routing.ModelCallExecutor;
import com.leo.careerforgeai.model.application.routing.TaskAwareModelRouter;
import com.leo.careerforgeai.model.config.*;
import com.leo.careerforgeai.model.domain.*;
import com.leo.careerforgeai.model.domain.routing.*;
import com.leo.careerforgeai.model.infrastructure.deepseek.DeepSeekChatClient;
import com.leo.careerforgeai.model.infrastructure.deepseek.stream.DeepSeekSseParser;
import com.leo.careerforgeai.model.infrastructure.kimi.KimiChatClient;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证DeepSeek受控故障经过同供应商重试后真实Fallback到Kimi。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
@EnabledIfEnvironmentVariable(named = "MOONSHOT_API_KEY", matches = ".+")
class ModelRoutingFallbackSmoke {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void shouldRetryDeepSeekThenFallbackToRealKimi() throws Exception {
        AtomicInteger deepseekAttempts = new AtomicInteger();
        HttpServer faultServer = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        faultServer.createContext("/chat/completions", exchange -> {
            deepseekAttempts.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] body = """
                    {"error":{"message":"controlled fault injection"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json");
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        faultServer.start();

        try {
            String deepseekModel = env(
                    "DEEPSEEK_MODEL", "deepseek-v4-flash");
            String kimiModel = env("KIMI_MODEL", "kimi-k2.6");
            URI deepseekBaseUrl = URI.create(
                    "http://127.0.0.1:" + faultServer.getAddress().getPort());
            URI kimiBaseUrl = URI.create(env(
                    "KIMI_BASE_URL", "https://api.moonshot.cn/v1"));

            ModelRoutingProperties routingProperties = routingProperties(
                    deepseekBaseUrl, deepseekModel, kimiBaseUrl, kimiModel);
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            ProviderModelClient deepseek = new DeepSeekChatClient(
                    new ModelProperties(
                            deepseekBaseUrl,
                            "controlled-fault-key",
                            deepseekModel
                    ),
                    jsonMapper,
                    new DeepSeekSseParser(jsonMapper),
                    httpClient
            );
            ProviderModelClient kimi = new KimiChatClient(
                    routingProperties, jsonMapper, httpClient);

            ModelReliabilityProperties reliability = reliabilityProperties();
            ModelReliabilityMetrics metrics = new ModelReliabilityMetrics();
            ModelCallExecutor executor = new ModelCallExecutor(
                    new TaskAwareModelRouter(
                            routingProperties.executionRoutes(),
                            routingProperties.version()
                    ),
                    List.of(deepseek, kimi),
                    new ModelCircuitBreaker(
                            reliability, Clock.systemUTC(), metrics),
                    new ModelCallBulkhead(
                            new ModelCallBulkheadProperties(2)),
                    reliability,
                    metrics,
                    ObservationRegistry.NOOP,
                    audit -> { }
            );

            ModelRequest request = new ModelRequest(
                    List.of(new ModelMessage(
                            ModelRole.USER,
                            "只返回两个大写英文字母：OK"
                    )),
                    ModelOutputFormat.TEXT,
                    32,
                    0.0,
                    TIMEOUT
            );

            long startedNanos = System.nanoTime();
            ModelResponse response = executor.chat(
                    ModelTaskType.MEMORY_EXTRACTION,
                    Set.of(),
                    request,
                    32,
                    true
            );
            long durationMs = Duration.ofNanos(
                    System.nanoTime() - startedNanos).toMillis();

            assertThat(deepseekAttempts).hasValue(2);
            assertThat(response.requestId()).isNotBlank();
            assertThat(response.model()).isEqualTo(kimiModel);
            assertThat(response.content()).isNotBlank();
            assertThat(response.usage()).isNotNull();
            assertThat(response.usage().totalTokens()).isPositive();
            assertThat(metrics.snapshot().logicalCalls()).isEqualTo(2);
            assertThat(metrics.snapshot().retryAttempts()).isEqualTo(1);
            assertThat(metrics.snapshot().failedAfterRetry()).isEqualTo(1);
            assertThat(metrics.snapshot().circuitRejectedCalls()).isZero();

            System.out.printf(
                    Locale.ROOT,
                    "caseId=CP4-REAL-FALLBACK, primary=deepseek, "
                            + "primaryAttempts=%d, fallback=kimi, model=%s, "
                            + "requestId=%s, outputChars=%d, totalTokens=%d, "
                            + "durationMs=%d%n",
                    deepseekAttempts.get(),
                    response.model(),
                    response.requestId(),
                    response.content().length(),
                    response.usage().totalTokens(),
                    durationMs
            );
        } finally {
            faultServer.stop(0);
        }
    }

    private ModelRoutingProperties routingProperties(
            URI deepseekBaseUrl,
            String deepseekModel,
            URI kimiBaseUrl,
            String kimiModel
    ) {
        Map<String, ModelRoutingProperties.Provider> providers = Map.of(
                "deepseek", new ModelRoutingProperties.Provider(
                        deepseekBaseUrl, "controlled-fault-key", true),
                "kimi", new ModelRoutingProperties.Provider(
                        kimiBaseUrl,
                        System.getenv("MOONSHOT_API_KEY"),
                        true)
        );
        Map<String, ModelRoutingProperties.Profile> profiles = Map.of(
                "deepseek-standard", profile(
                        "deepseek", deepseekModel, "deepseek-smoke"),
                "kimi-standard", profile(
                        "kimi", kimiModel, "kimi-smoke")
        );
        return new ModelRoutingProperties(
                "routing-cp4-smoke",
                providers,
                profiles,
                Map.of(
                        ModelTaskType.MEMORY_EXTRACTION,
                        List.of("deepseek-standard", "kimi-standard")
                )
        );
    }

    private ModelRoutingProperties.Profile profile(
            String provider,
            String model,
            String priceVersion
    ) {
        return new ModelRoutingProperties.Profile(
                provider,
                model,
                Set.of(ModelCapability.CHAT),
                ReasoningMode.DISABLED,
                null,
                64,
                TIMEOUT,
                priceVersion,
                true
        );
    }

    private ModelReliabilityProperties reliabilityProperties() {
        return new ModelReliabilityProperties(
                2,
                Duration.ofMillis(1),
                Duration.ofMillis(1),
                1.0,
                Duration.ofSeconds(1),
                10,
                10,
                100.0F,
                Duration.ofSeconds(30),
                1
        );
    }

    private String env(String name, String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }
}
