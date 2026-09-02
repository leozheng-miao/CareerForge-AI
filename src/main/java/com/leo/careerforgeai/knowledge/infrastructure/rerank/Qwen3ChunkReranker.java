package com.leo.careerforgeai.knowledge.infrastructure.rerank;

import com.leo.careerforgeai.knowledge.application.rerank.ChunkRerankException;
import com.leo.careerforgeai.knowledge.application.rerank.ChunkReranker;
import com.leo.careerforgeai.knowledge.config.Qwen3RerankProperties;
import com.leo.careerforgeai.knowledge.domain.rerank.ChunkRerankResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @program: CareerForge-AI
 * @description: 调用Qwen3 Cross-Encoder重排候选并安全映射回原始RRF对象。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
@Component
@ConditionalOnProperty(
        prefix = "careerforge.knowledge.rerank",
        name = "provider",
        havingValue = "qwen3"
)
@Slf4j
public final class Qwen3ChunkReranker implements ChunkReranker {

    private static final int MAX_QUERY_CHARS = 2_000;
    private final Qwen3RerankProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public Qwen3ChunkReranker(
            Qwen3RerankProperties properties,
            JsonMapper jsonMapper,
            HttpClient httpClient
    ) {
        this.properties = Objects.requireNonNull(
                properties, "properties不能为空");
        properties.requiredEndpoint();
        properties.requiredApiKey();
        this.jsonMapper = Objects.requireNonNull(
                jsonMapper, "jsonMapper不能为空");
        this.httpClient = Objects.requireNonNull(
                httpClient, "httpClient不能为空");
    }

    @Override
    public ChunkRerankResult rerank(
            String query, List<RrfRankedChunk> candidates) {
        validateInput(query, candidates);
        if (candidates.isEmpty()) return ChunkRerankResult.notCalled();

        List<String> documents = candidates.stream()
                .map(this::documentText)
                .toList();
        long startedNanos = System.nanoTime();

        try {
            HttpResponse<String> response = send(
                    buildRequest(query, documents));
            if (response.statusCode() < 200
                    || response.statusCode() > 299) {
                throw new ChunkRerankException(
                        "Qwen3 Rerank供应商调用失败，statusCode="
                                + response.statusCode());
            }

            ParsedResult parsed = parseResponse(
                    response.body(), candidates);
            long durationMs = Duration.ofNanos(
                    System.nanoTime() - startedNanos).toMillis();

            log.info(
                    "Qwen3 Rerank完成，requestId={}, model={}, candidates={}, durationMs={}, totalTokens={}",
                    parsed.requestId(), properties.model(),
                    candidates.size(), durationMs, parsed.totalTokens());

            return new ChunkRerankResult(
                    parsed.rankedChunks(),
                    properties.model(),
                    parsed.totalTokens(),
                    0,
                    parsed.totalTokens()
            );
        } catch (ChunkRerankException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ChunkRerankException(
                    "Qwen3 Rerank调用失败", exception);
        }
    }

    private HttpRequest buildRequest(
            String query, List<String> documents) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("query", query);
        body.put("documents", documents);
        body.put("top_n", documents.size());
        body.put("instruct", properties.instruct());

        try {
            return HttpRequest.newBuilder(properties.requiredEndpoint())
                    .timeout(properties.timeout())
                    .header(
                            "Authorization",
                            "Bearer " + properties.requiredApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            jsonMapper.writeValueAsString(body),
                            StandardCharsets.UTF_8))
                    .build();
        } catch (JacksonException exception) {
            throw new ChunkRerankException(
                    "Qwen3 Rerank请求序列化失败", exception);
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        var future = httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        try {
            return future.get(
                    properties.timeout().toNanos(),
                    TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new ChunkRerankException(
                    "Qwen3 Rerank响应超时", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ChunkRerankException(
                    "Qwen3 Rerank调用被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            while (cause instanceof CompletionException
                    && cause.getCause() != null) {
                cause = cause.getCause();
            }
            throw new ChunkRerankException(
                    "Qwen3 Rerank网络调用失败", cause);
        }
    }

    private ParsedResult parseResponse(
            String body, List<RrfRankedChunk> candidates) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(body);
        } catch (JacksonException exception) {
            throw new ChunkRerankException(
                    "Qwen3 Rerank响应不是合法JSON", exception);
        }

        String requestId = firstNonBlank(
                root.path("id").asText(),
                root.path("request_id").asText());
        if (requestId == null) {
            throw new ChunkRerankException(
                    "Qwen3 Rerank响应缺少requestId");
        }
        if (!properties.model().equals(root.path("model").asText())) {
            throw new ChunkRerankException(
                    "Qwen3 Rerank响应模型不匹配");
        }

        JsonNode results = root.path("results");
        if (!results.isArray()
                || results.size() != candidates.size()) {
            throw new ChunkRerankException(
                    "Qwen3 Rerank遗漏了候选结果");
        }

        Map<Integer, Double> scoreByIndex = new HashMap<>();
        for (JsonNode result : results) {
            if (!result.path("index").isIntegralNumber()
                    || !result.path("relevance_score").isNumber()) {
                throw new ChunkRerankException(
                        "Qwen3 Rerank结果字段非法");
            }
            int index = result.path("index").asInt();
            double score = result.path("relevance_score").asDouble();
            if (index < 0 || index >= candidates.size()
                    || !Double.isFinite(score)
                    || score < 0.0 || score > 1.0
                    || scoreByIndex.putIfAbsent(index, score) != null) {
                throw new ChunkRerankException(
                        "Qwen3 Rerank候选下标或分数非法");
            }
        }
        if (scoreByIndex.size() != candidates.size()) {
            throw new ChunkRerankException(
                    "Qwen3 Rerank候选集合不完整");
        }

        long totalTokens = root.path("usage")
                .path("total_tokens").asLong(-1);
        if (totalTokens <= 0) {
            throw new ChunkRerankException(
                    "Qwen3 Rerank usage非法");
        }

        List<RrfRankedChunk> rankedChunks = scoreByIndex.entrySet()
                .stream()
                .sorted(Comparator
                        .<Map.Entry<Integer, Double>>comparingDouble(
                                Map.Entry::getValue)
                        .reversed()
                        .thenComparingInt(Map.Entry::getKey))
                .map(entry -> candidates.get(entry.getKey()))
                .toList();

        return new ParsedResult(
                requestId, rankedChunks, totalTokens);
    }

    private void validateInput(
            String query, List<RrfRankedChunk> candidates) {
        if (query == null || query.isBlank()
                || query.length() > MAX_QUERY_CHARS) {
            throw new IllegalArgumentException(
                    "query不能为空且长度不能超过2000");
        }
        if (candidates == null) {
            throw new IllegalArgumentException(
                    "candidates不能为空");
        }
        if (candidates.size() > properties.maxCandidates()) {
            throw new IllegalArgumentException(
                    "candidates超过Qwen3 Rerank数量上限");
        }

        Set<String> chunkIds = new HashSet<>();
        for (int index = 0; index < candidates.size(); index++) {
            RrfRankedChunk candidate = candidates.get(index);
            if (candidate == null) {
                throw new IllegalArgumentException(
                        "candidates不能包含null");
            }
            if (candidate.finalRank() != index + 1) {
                throw new IllegalArgumentException(
                        "候选必须按照连续RRF finalRank排列");
            }
            if (!chunkIds.add(candidate.chunk().chunkId())) {
                throw new IllegalArgumentException(
                        "candidates包含重复chunkId");
            }
        }
    }

    private String documentText(RrfRankedChunk candidate) {
        String text = candidate.chunk().retrievalText();
        return text.length() <= properties.maxDocumentChars()
                ? text
                : text.substring(0, properties.maxDocumentChars());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    /**
     * @param requestId 供应商请求ID
     * @param rankedChunks 经过分数排序的原始候选
     * @param totalTokens 供应商报告的总Token
     */
    private record ParsedResult(
            String requestId,
            List<RrfRankedChunk> rankedChunks,
            long totalTokens
    ) {}
}