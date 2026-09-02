package com.leo.careerforgeai.knowledge.infrastructure.rerank;

import tools.jackson.databind.json.JsonMapper;
import com.leo.careerforgeai.knowledge.config.Qwen3RerankProperties;

import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.rerank.ChunkRerankResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实验证Qwen3重排序生产适配器的协议兼容性、相关性排序和Token用量映射。
 */
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(
        named = "DASHSCOPE_RERANK_ENDPOINT",
        matches = "https://.+/reranks")
class Qwen3ChunkRerankerSmoke {

    @Test
    void shouldRerankCandidatesThroughProductionAdapter() {
        String endpoint = System.getenv("DASHSCOPE_RERANK_ENDPOINT");
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        String model = System.getenv().getOrDefault("DASHSCOPE_RERANK_MODEL", "qwen3-rerank");
        Qwen3RerankProperties properties = new Qwen3RerankProperties(
                endpoint, apiKey, model, 20, 4000, Duration.ofSeconds(60),
                "Given a web search query, retrieve relevant passages that answer the query.");
        Qwen3ChunkReranker reranker = new Qwen3ChunkReranker(
                properties, JsonMapper.builder().build(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());

        List<RrfRankedChunk> candidates = List.of(
                candidate("a".repeat(64), 1,
                        "ThreadLocal使用弱引用保存键，但值仍可能被线程长期持有，因此在线程池中必须调用remove避免内存泄漏。"),
                candidate("b".repeat(64), 2,
                        "咖啡豆的烘焙程度会影响酸度、香气和苦味。"),
                candidate("c".repeat(64), 3,
                        "Java虚拟线程适合大量阻塞式IO任务，但不应长期固定在平台线程上。"),
                candidate("d".repeat(64), 4,
                        "垃圾回收器通过可达性分析判断对象是否仍然存活。"));

        long startedAt = System.nanoTime();
        ChunkRerankResult result = reranker.rerank(
                "Java线程池中使用ThreadLocal为什么可能造成内存泄漏，应该如何处理？",
                candidates);
        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertThat(result.model()).isEqualTo(model);
        assertThat(result.rankedChunks()).containsExactlyInAnyOrderElementsOf(candidates);
        assertThat(result.rankedChunks().getFirst()).isEqualTo(candidates.getFirst());
        assertThat(result.inputTokens()).isPositive();
        assertThat(result.outputTokens()).isZero();
        assertThat(result.totalTokens()).isEqualTo(result.inputTokens());

        int topOriginalIndex = candidates.indexOf(result.rankedChunks().getFirst());
        System.out.printf(
                "provider=ALIBABA_CLOUD, mode=PRODUCTION_ADAPTER, model=%s, candidates=%d, "
                        + "topOriginalIndex=%d, totalTokens=%d, durationMs=%d%n",
                result.model(), result.rankedChunks().size(), topOriginalIndex,
                result.totalTokens(), durationMs);
    }

    private static RrfRankedChunk candidate(
            String chunkId, int rank, String content) {
        DocumentChunk chunk = new DocumentChunk(
                "careerforge", "document-1", "Java并发面试经验",
                KnowledgeDocumentType.INTERVIEW_EXPERIENCE, "java-concurrency.md",
                "f".repeat(64), "markdown-cleaner-v2",
                "markdown-structure-v2|max=1000|overlap=120",
                chunkId, rank - 1, List.of("Java并发面试经验", "ThreadLocal"),
                0, content.length(), content);
        return new RrfRankedChunk(chunk, rank, null, 1D / (60 + rank), rank);
    }
}