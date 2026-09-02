package com.leo.careerforgeai.knowledge.infrastructure.rerank;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.leo.careerforgeai.knowledge.application.rerank.ChunkRerankException;
import com.leo.careerforgeai.knowledge.config.Qwen3RerankProperties;

import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.rerank.ChunkRerankResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证Qwen3重排序适配器的请求映射、响应校验、稳定排序和错误脱敏。
 */
class Qwen3ChunkRerankerTest {

    private static final String INSTRUCT =
            "Given a web search query, retrieve relevant passages that answer the query.";
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void shouldMapRequestAndRestoreOriginalCandidatesByReturnedIndex() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        String response = """
                {
                  "id":"rerank-request-1",
                  "model":"qwen3-rerank",
                  "results":[
                    {"index":2,"relevance_score":0.91},
                    {"index":0,"relevance_score":0.70},
                    {"index":1,"relevance_score":0.10}
                  ],
                  "usage":{"total_tokens":42}
                }
                """;
        HttpServer server = startServer(200, response, requestBody);
        try {
            List<RrfRankedChunk> candidates = candidates();
            ChunkRerankResult result = reranker(server).rerank("ThreadLocal 为什么会造成内存泄漏？", candidates);
            assertThat(result.rankedChunks()).containsExactly(candidates.get(2), candidates.get(0), candidates.get(1));
            assertThat(result.model()).isEqualTo("qwen3-rerank");
            assertThat(result.inputTokens()).isEqualTo(42);
            assertThat(result.outputTokens()).isZero();
            assertThat(result.totalTokens()).isEqualTo(42);

            JsonNode request = jsonMapper.readTree(requestBody.get());
            assertThat(request.path("model").asText()).isEqualTo("qwen3-rerank");
            assertThat(request.path("query").asText()).isEqualTo("ThreadLocal 为什么会造成内存泄漏？");
            assertThat(request.path("top_n").asInt()).isEqualTo(3);
            assertThat(request.path("instruct").asText()).isEqualTo(INSTRUCT);
            assertThat(request.path("documents")).hasSize(3);
            assertThat(request.path("documents").get(0).asText()).contains("候选正文-aaaa");
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @MethodSource("invalidResponses")
    void shouldRejectIncompleteOrInvalidProviderResponse(String response) throws Exception {
        HttpServer server = startServer(200, response, new AtomicReference<>());
        try {
            assertThatThrownBy(() -> reranker(server).rerank("Java并发问题", candidates()))
                    .isInstanceOf(ChunkRerankException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldNotExposeProviderErrorBody() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startServer(429, "{\"error\":\"secret-provider-detail\"}", requestBody);
        try {
            assertThatThrownBy(() -> reranker(server).rerank("Java并发问题", candidates()))
                    .isInstanceOf(ChunkRerankException.class)
                    .hasMessageContaining("429")
                    .hasMessageNotContaining("secret-provider-detail");
        } finally {
            server.stop(0);
        }
    }

    private Qwen3ChunkReranker reranker(HttpServer server) {
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort()
                + "/compatible-api/v1/reranks";
        Qwen3RerankProperties properties = new Qwen3RerankProperties(
                endpoint, "test-api-key", "qwen3-rerank", 20, 4000,
                Duration.ofSeconds(5), INSTRUCT);
        return new Qwen3ChunkReranker(
                properties, jsonMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    private HttpServer startServer(
            int status, String response, AtomicReference<String> requestBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/compatible-api/v1/reranks", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        return server;
    }

    private static Stream<String> invalidResponses() {
        return Stream.of(
                """
                {"id":"request-1","model":"qwen3-rerank",
                 "results":[
                   {"index":0,"relevance_score":0.9},
                   {"index":0,"relevance_score":0.8},
                   {"index":2,"relevance_score":0.7}
                 ],
                 "usage":{"total_tokens":10}}
                """,
                """
                {"id":"request-1","model":"qwen3-rerank",
                 "results":[
                   {"index":0,"relevance_score":0.9},
                   {"index":1,"relevance_score":0.8},
                   {"index":3,"relevance_score":0.7}
                 ],
                 "usage":{"total_tokens":10}}
                """,
                """
                {"id":"request-1","model":"qwen3-rerank",
                 "results":[
                   {"index":0,"relevance_score":1.1},
                   {"index":1,"relevance_score":0.8},
                   {"index":2,"relevance_score":0.7}
                 ],
                 "usage":{"total_tokens":10}}
                """,
                """
                {"id":"request-1","model":"qwen3-rerank",
                 "results":[
                   {"index":0,"relevance_score":0.9},
                   {"index":1,"relevance_score":0.8}
                 ],
                 "usage":{"total_tokens":10}}
                """,
                """
                {"id":"request-1","model":"unexpected-model",
                 "results":[
                   {"index":0,"relevance_score":0.9},
                   {"index":1,"relevance_score":0.8},
                   {"index":2,"relevance_score":0.7}
                 ],
                 "usage":{"total_tokens":10}}
                """,
                """
                {"id":"request-1","model":"qwen3-rerank",
                 "results":[
                   {"index":0,"relevance_score":0.9},
                   {"index":1,"relevance_score":0.8},
                   {"index":2,"relevance_score":0.7}
                 ],
                 "usage":{"total_tokens":0}}
                """
        );
    }

    private static List<RrfRankedChunk> candidates() {
        return List.of(
                candidate("a".repeat(64), 1),
                candidate("b".repeat(64), 2),
                candidate("c".repeat(64), 3)
        );
    }

    private static RrfRankedChunk candidate(String chunkId, int rank) {
        String content = "候选正文-" + chunkId.substring(0, 4);
        DocumentChunk chunk = new DocumentChunk(
                "careerforge", "document-1", "测试文档",
                KnowledgeDocumentType.INTERVIEW_EXPERIENCE, "测试文档.md",
                "f".repeat(64), "markdown-cleaner-v2",
                "markdown-structure-v2|max=1000|overlap=120",
                chunkId, rank - 1, List.of("测试文档", "Java 并发"),
                0, content.length(), content);
        return new RrfRankedChunk(chunk, rank, null, 1D / (60 + rank), rank);
    }
}