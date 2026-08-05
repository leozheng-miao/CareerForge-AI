package com.leo.careerforgeai.knowledge.application.rerank;

import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.rerank.ChunkRerankResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankStatus;
import com.leo.careerforgeai.knowledge.domain.rerank.RerankedRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalComparisonResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class KnowledgeRerankingServiceTest {

    private static final String A_ID = "a".repeat(64);
    private static final String B_ID = "b".repeat(64);
    private static final String C_ID = "c".repeat(64);

    private final ChunkReranker chunkReranker = mock(ChunkReranker.class);
    private final KnowledgeRerankingService service = new KnowledgeRerankingService(chunkReranker);

    /** 验证成功 Rerank，并将返回对象重新映射为本次 RRF 原始候选。 */
    @Test
    void shouldApplyRerankAndReuseOriginalCandidates() {
        List<RrfRankedChunk> candidates = List.of(candidate(A_ID, 1, "候选 A"), candidate(B_ID, 2, "候选 B"));
        HybridRetrievalResult hybridResult = hybrid(candidates);
        RrfRankedChunk changedB = candidate(B_ID, 2, "被模型修改的候选 B");

        when(chunkReranker.rerank("Java 并发", candidates)).thenReturn(rerankResult(List.of(changedB, candidates.getFirst())));

        RerankedRetrievalResult result = service.rerank("Java 并发", hybridResult, true);

        assertThat(result.status()).isEqualTo(RerankStatus.APPLIED);
        assertThat(result.rankedChunks()).containsExactly(candidates.get(1), candidates.get(0));
        assertThat(result.rankedChunks().getFirst()).isSameAs(candidates.get(1));
        assertThat(result.rerankDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.rerankDurationMs()).isGreaterThanOrEqualTo(0);

        assertThat(result.rerankModel()).isEqualTo("test-rerank-model");
        assertThat(result.rerankInputTokens()).isEqualTo(200);
        assertThat(result.rerankOutputTokens()).isEqualTo(30);
        assertThat(result.rerankTotalTokens()).isEqualTo(230);
    }

    /** 验证关闭和空候选时不会调用模型。 */
    @Test
    void shouldSkipModelWhenDisabledOrCandidatesAreEmpty() {
        HybridRetrievalResult populated = hybrid(List.of(candidate(A_ID, 1, "候选 A")));
        HybridRetrievalResult empty = hybrid(List.of());

        RerankedRetrievalResult disabled = service.rerank("Java 并发", populated, false);
        RerankedRetrievalResult skippedEmpty = service.rerank("Java 并发", empty, true);

        assertThat(disabled.status()).isEqualTo(RerankStatus.DISABLED);
        assertThat(disabled.rankedChunks()).containsExactlyElementsOf(populated.rrfChunks());
        assertThat(skippedEmpty.status()).isEqualTo(RerankStatus.SKIPPED_EMPTY);
        assertThat(skippedEmpty.rankedChunks()).isEmpty();
        assertThat(disabled.status()).isEqualTo(RerankStatus.DISABLED);
        assertThat(disabled.rankedChunks()).containsExactlyElementsOf(populated.rrfChunks());
        assertThat(disabled.rerankModel()).isNull();
        assertThat(disabled.rerankTotalTokens()).isZero();

        assertThat(skippedEmpty.status()).isEqualTo(RerankStatus.SKIPPED_EMPTY);
        assertThat(skippedEmpty.rankedChunks()).isEmpty();
        assertThat(skippedEmpty.rerankModel()).isNull();
        assertThat(skippedEmpty.rerankTotalTokens()).isZero();

        verifyNoInteractions(chunkReranker);
        verifyNoInteractions(chunkReranker);
    }

    /** 验证模型调用失败时完整回退到原始 RRF 顺序。 */
    @Test
    void shouldFallbackToRrfWhenRerankerFails() {
        List<RrfRankedChunk> candidates = List.of(candidate(A_ID, 1, "候选 A"), candidate(B_ID, 2, "候选 B"));
        HybridRetrievalResult hybridResult = hybrid(candidates);
        when(chunkReranker.rerank("Java 并发", candidates)).thenThrow(new ChunkRerankException("provider unavailable"));

        RerankedRetrievalResult result = service.rerank("Java 并发", hybridResult, true);

        assertThat(result.status()).isEqualTo(RerankStatus.FALLBACK);
        assertThat(result.rankedChunks()).containsExactlyElementsOf(candidates);
        assertThat(result.rerankDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.status()).isEqualTo(RerankStatus.FALLBACK);
        assertThat(result.rankedChunks()).containsExactlyElementsOf(candidates);
        assertThat(result.rerankDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.rerankModel()).isNull();
        assertThat(result.rerankTotalTokens()).isZero();
    }

    /** 验证未知候选无法进入最终结果，并触发 RRF 回退。 */
    @Test
    void shouldFallbackWhenRerankerReturnsUnknownCandidate() {
        List<RrfRankedChunk> candidates = List.of(candidate(A_ID, 1, "候选 A"), candidate(B_ID, 2, "候选 B"));
        HybridRetrievalResult hybridResult = hybrid(candidates);
        RrfRankedChunk unknown = candidate(C_ID, 2, "未知候选");
        when(chunkReranker.rerank("Java 并发", candidates)).thenReturn(rerankResult(List.of(candidates.getFirst(), unknown)));

        RerankedRetrievalResult result = service.rerank("Java 并发", hybridResult, true);

        assertThat(result.status()).isEqualTo(RerankStatus.FALLBACK);
        assertThat(result.rankedChunks()).containsExactlyElementsOf(candidates);
        assertThat(result.status()).isEqualTo(RerankStatus.FALLBACK);
        assertThat(result.rankedChunks()).containsExactlyElementsOf(candidates);
        assertThat(result.rerankModel()).isNull();
        assertThat(result.rerankTotalTokens()).isZero();
    }

    /** 验证基础输入在调用 Reranker 前完成校验。 */
    @Test
    void shouldRejectInvalidInputBeforeRerank() {
        assertThatThrownBy(() -> service.rerank(" ", hybrid(List.of()), true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.rerank("Java 并发", null, true)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(chunkReranker);
    }

    private HybridRetrievalResult hybrid(List<RrfRankedChunk> candidates) {
        RetrievalResult emptyRoute = new RetrievalResult(List.of(), 0);
        RetrievalComparisonResult comparison = new RetrievalComparisonResult(emptyRoute, emptyRoute, "test-embedding-model", 1024, 0);
        return new HybridRetrievalResult(comparison, candidates, 0);
    }

    private RrfRankedChunk candidate(String chunkId, int rank, String content) {
        DocumentChunk chunk = new DocumentChunk(
                "careerforge",
                "document-1",
                "测试文档",
                KnowledgeDocumentType.INTERVIEW_EXPERIENCE,
                "测试文档.md",
                "f".repeat(64),
                "markdown-cleaner-v2",
                "markdown-structure-v2|max=1000|overlap=120",
                chunkId,
                rank - 1,
                List.of("测试文档"),
                0,
                content.length(),
                content
        );
        return new RrfRankedChunk(chunk, rank, null, 1D / (60 + rank), rank);
    }

    private ChunkRerankResult rerankResult(List<RrfRankedChunk> rankedChunks) {
        return new ChunkRerankResult(rankedChunks, "test-rerank-model", 200, 30, 230);
    }
}