package com.leo.careerforgeai.model.infrastructure.rerank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 直接调用阿里云百炼qwen3-rerank验证候选集合、相关性分数、使用量和错误信封
 * @author: Miao Zheng
 * @date: 2026-09-01
 */
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class Qwen3RerankProtocolSmoke {

    private static final String QUERY =
            "Java线程池复用线程时，如何避免ThreadLocal残留数据导致内存泄漏？";
    private static final List<String> DOCUMENTS = List.of(
            "在线程池中使用ThreadLocal后，应在finally块调用remove，避免线程复用时残留数据。",
            "制作手冲咖啡时可以调整水温、研磨度和冲泡时间。",
            "虚拟线程适合大量阻塞式I/O任务，但不应长期固定绑定平台线程。",
            "Java垃圾收集器负责回收不可达对象，但不能替代业务代码清理线程局部变量。"
    );
    private static final String INSTRUCT =
            "Given a web search query, retrieve relevant passages that answer the query.";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 验证真实重排结果保持候选集合完整且分数合法。 */
    @Test
    void shouldRerankEveryCandidateWithoutMutation() throws Exception {
        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post(apiKey());
        long durationMs = elapsedMillis(startedNanos);

        assertThat(response.statusCode())
                .withFailMessage(
                        "期望HTTP 200，实际状态=%s，bodyHash=%s",
                        response.statusCode(),
                        sha256(response.body())
                )
                .isEqualTo(200);

        JsonNode root = requireJson(response);
        JsonNode results = root.path("results");

        assertThat(root.path("model").asText()).isEqualTo(model());
        assertThat(root.path("id").asText()).isNotBlank();
        assertThat(results.isArray()).isTrue();
        assertThat(results.size()).isEqualTo(DOCUMENTS.size());

        List<Integer> indexes = new ArrayList<>();
        List<Double> scores = new ArrayList<>();

        for (JsonNode result : results) {
            assertThat(result.path("index").isIntegralNumber()).isTrue();
            assertThat(result.path("relevance_score").isNumber()).isTrue();

            int index = result.path("index").asInt();
            double score = result.path("relevance_score").asDouble();

            assertThat(index).isBetween(0, DOCUMENTS.size() - 1);
            assertThat(Double.isFinite(score)).isTrue();
            assertThat(score).isBetween(0.0, 1.0);

            indexes.add(index);
            scores.add(score);
        }

        assertThat(indexes).containsExactlyInAnyOrder(0, 1, 2, 3);
        assertThat(indexes.getFirst()).isEqualTo(0);

        for (int index = 1; index < scores.size(); index++) {
            assertThat(scores.get(index))
                    .isLessThanOrEqualTo(scores.get(index - 1));
        }

        long totalTokens = root.path("usage").path("total_tokens").asLong();
        assertThat(totalTokens).isPositive();

        System.out.printf(
                Locale.ROOT,
                "provider=ALIBABA_CLOUD, mode=RERANK, model=%s, "
                        + "providerRequestId=%s, candidateCount=%d, "
                        + "resultCount=%d, topIndex=%d, topScore=%.6f, "
                        + "minScore=%.6f, totalTokens=%d, durationMs=%d%n",
                root.path("model").asText(),
                root.path("id").asText(),
                DOCUMENTS.size(),
                results.size(),
                indexes.getFirst(),
                scores.getFirst(),
                scores.getLast(),
                totalTokens,
                durationMs
        );
    }

    /** 验证无效凭证返回可追踪且不泄漏真实Key的错误信封。 */
    @Test
    void shouldExposeErrorEnvelopeForInvalidApiKey() throws Exception {
        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post(
                "invalid-qwen3-rerank-smoke-key"
        );
        long durationMs = elapsedMillis(startedNanos);

        assertThat(response.statusCode()).isBetween(400, 499);

        JsonNode root = requireJson(response);
        JsonNode error = root.path("error");

        String errorCode = firstNonBlank(
                root.path("code").asText(),
                error.path("code").asText(),
                error.path("type").asText()
        );
        String errorMessage = firstNonBlank(
                root.path("message").asText(),
                error.path("message").asText()
        );
        String requestId = firstNonBlank(
                root.path("request_id").asText(),
                root.path("id").asText(),
                response.headers()
                        .firstValue("x-request-id")
                        .orElse("")
        );

        assertThat(errorCode).isNotEqualTo("UNKNOWN");
        assertThat(errorMessage).isNotEqualTo("UNKNOWN");
        assertThat(requestId).isNotEqualTo("UNKNOWN");
        assertThat(response.body()).doesNotContain(apiKey());

        System.out.printf(
                Locale.ROOT,
                "provider=ALIBABA_CLOUD, mode=ERROR_PROTOCOL, "
                        + "httpStatus=%d, providerRequestId=%s, "
                        + "errorCode=%s, bodyChars=%d, bodyHash=%s, "
                        + "durationMs=%d%n",
                response.statusCode(),
                requestId,
                errorCode,
                response.body().length(),
                sha256(response.body()),
                durationMs
        );
    }

    private HttpResponse<String> post(String key) throws Exception {
        String requestBody = jsonMapper.writeValueAsString(requestBody());
        HttpRequest request = HttpRequest.newBuilder(endpoint())
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        requestBody,
                        StandardCharsets.UTF_8
                ))
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    private Map<String, Object> requestBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("documents", DOCUMENTS);
        body.put("query", QUERY);
        body.put("top_n", DOCUMENTS.size());
        body.put("instruct", INSTRUCT);
        return body;
    }

    private JsonNode requireJson(HttpResponse<String> response)
            throws Exception {
        assertThat(response.body()).isNotBlank();
        JsonNode root = jsonMapper.readTree(response.body());
        assertThat(root.isObject()).isTrue();
        return root;
    }

    private static URI endpoint() {
        String workspaceId = requiredEnvironmentVariable(
                "DASHSCOPE_WORKSPACE_ID"
        );

        if (!workspaceId.matches("[A-Za-z0-9-]+")) {
            throw new IllegalStateException(
                    "DASHSCOPE_WORKSPACE_ID包含非法字符"
            );
        }

        return URI.create(
                "https://" + workspaceId
                        + ".cn-beijing.maas.aliyuncs.com"
                        + "/compatible-api/v1/reranks"
        );
    }

    private static String model() {
        String configuredModel = System.getenv()
                .getOrDefault(
                        "DASHSCOPE_RERANK_MODEL",
                        "qwen3-rerank"
                )
                .trim();

        if (!"qwen3-rerank".equals(configuredModel)) {
            throw new IllegalStateException(
                    "本Smoke只验证qwen3-rerank协议"
            );
        }

        return configuredModel;
    }

    private static String apiKey() {
        return requiredEnvironmentVariable("DASHSCOPE_API_KEY");
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + "未配置");
        }

        return value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "UNKNOWN";
    }

    private static long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(
                System.nanoTime() - startedNanos
        ).toMillis();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}