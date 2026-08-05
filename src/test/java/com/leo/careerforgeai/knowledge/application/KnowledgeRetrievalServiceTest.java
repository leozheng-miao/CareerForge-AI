package com.leo.careerforgeai.knowledge.application;

import com.leo.careerforgeai.knowledge.domain.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalComparisonResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievedChunk;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import com.leo.careerforgeai.model.application.EmbeddingGateway;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingPurpose;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingRequest;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-04 15:32
 **/
class KnowledgeRetrievalServiceTest {

    private final Bm25Retriever bm25Retriever = mock(Bm25Retriever.class);
    private final VectorRetriever vectorRetriever = mock(VectorRetriever.class);
    private final EmbeddingGateway embeddingGateway = mock(EmbeddingGateway.class);
    private final RrfFusion rrfFusion = mock(RrfFusion.class);
    private final KnowledgeRetrievalService service = new KnowledgeRetrievalService(bm25Retriever, vectorRetriever, embeddingGateway, rrfFusion);

    @Test
    void shouldGenerateQueryEmbeddingAndReturnBothResults() {
        String query = "如何提高 RAG 召回率";
        RetrievalScope scope = new RetrievalScope("careerforge", Set.of(), Set.of());
        RetrievalResult bm25Result = result("a", 8.2D, 12);
        RetrievalResult vectorResult = result("b", 0.91D, 8);
        List<Float> queryVector = List.of(0.1F, 0.2F);
        EmbeddingResult embeddingResult = new EmbeddingResult("qwen3-embedding:0.6b", 2, List.of(queryVector), 20);

        when(bm25Retriever.retrieve(query, scope, 5)).thenReturn(bm25Result);
        when(embeddingGateway.embed(any(EmbeddingRequest.class))).thenReturn(embeddingResult);
        when(vectorRetriever.retrieve(queryVector, scope, 5, 50)).thenReturn(vectorResult);

        RetrievalComparisonResult result = service.retrieveBoth(query, scope, 5, 50);

        ArgumentCaptor<EmbeddingRequest> embeddingCaptor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingGateway).embed(embeddingCaptor.capture());
        assertThat(embeddingCaptor.getValue().purpose()).isEqualTo(EmbeddingPurpose.QUERY);
        assertThat(embeddingCaptor.getValue().inputs()).containsExactly(query);

        assertThat(result.bm25Result()).isSameAs(bm25Result);
        assertThat(result.vectorResult()).isSameAs(vectorResult);
        assertThat(result.embeddingModel()).isEqualTo("qwen3-embedding:0.6b");
        assertThat(result.embeddingDimensions()).isEqualTo(2);
        assertThat(result.queryEmbeddingDurationMs()).isEqualTo(20);
        assertThat(result.vectorTotalDurationMs()).isEqualTo(28);

        InOrder order = inOrder(bm25Retriever, embeddingGateway, vectorRetriever);
        order.verify(bm25Retriever).retrieve(query, scope, 5);
        order.verify(embeddingGateway).embed(any(EmbeddingRequest.class));
        order.verify(vectorRetriever).retrieve(queryVector, scope, 5, 50);
    }

    @Test
    void shouldStopBeforeVectorSearchWhenQueryEmbeddingFails() {
        String query = "RAG";
        RetrievalScope scope = new RetrievalScope("careerforge", Set.of(), Set.of());
        RuntimeException failure = new RuntimeException("Ollama unavailable");

        when(bm25Retriever.retrieve(query, scope, 5)).thenReturn(result("a", 5.0D, 5));
        when(embeddingGateway.embed(any(EmbeddingRequest.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.retrieveBoth(query, scope, 5, 50)).isSameAs(failure);
        verifyNoInteractions(vectorRetriever, rrfFusion);
    }

    @Test
    void shouldRejectInvalidParametersBeforeCallingDependencies() {
        RetrievalScope scope = new RetrievalScope("careerforge", Set.of(), Set.of());

        assertThatThrownBy(() -> service.retrieveBoth(" ", scope, 5, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.retrieveBoth("RAG", scope, 0, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.retrieveBoth("RAG", scope, 10, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.retrieveHybrid("RAG", scope, 5, 50, 0)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(bm25Retriever, embeddingGateway, vectorRetriever, rrfFusion);
    }

    /** 验证应用服务使用更大的候选集合执行 RRF 并返回最终混合结果。 */
    @Test
    void shouldRetrieveCandidatesAndReturnHybridResult() {
        String query = "Java 并发";
        RetrievalScope scope = new RetrievalScope("careerforge", Set.of(), Set.of());
        RetrievalResult bm25Result = result("a", 8.2D, 12);
        RetrievalResult vectorResult = result("b", 0.91D, 8);
        List<Float> queryVector = List.of(0.1F, 0.2F);
        EmbeddingResult embeddingResult = new EmbeddingResult("qwen3-embedding:0.6b", 2, List.of(queryVector), 20);
        RrfRankedChunk fusedChunk = new RrfRankedChunk(bm25Result.chunks().getFirst().chunk(), 1, null, 1D / 61, 1);
        List<RrfRankedChunk> fusedChunks = List.of(fusedChunk);

        when(bm25Retriever.retrieve(query, scope, 10)).thenReturn(bm25Result);
        when(embeddingGateway.embed(any(EmbeddingRequest.class))).thenReturn(embeddingResult);
        when(vectorRetriever.retrieve(queryVector, scope, 10, 50)).thenReturn(vectorResult);
        when(rrfFusion.fuse(bm25Result, vectorResult, 5)).thenReturn(fusedChunks);

        HybridRetrievalResult result = service.retrieveHybrid(query, scope, 10, 50, 5);

        assertThat(result.comparisonResult().bm25Result()).isSameAs(bm25Result);
        assertThat(result.comparisonResult().vectorResult()).isSameAs(vectorResult);
        assertThat(result.rrfChunks()).containsExactlyElementsOf(fusedChunks);
        assertThat(result.fusionDurationMs()).isGreaterThanOrEqualTo(0);

        InOrder order = inOrder(bm25Retriever, embeddingGateway, vectorRetriever, rrfFusion);
        order.verify(bm25Retriever).retrieve(query, scope, 10);
        order.verify(embeddingGateway).embed(any(EmbeddingRequest.class));
        order.verify(vectorRetriever).retrieve(queryVector, scope, 10, 50);
        order.verify(rrfFusion).fuse(bm25Result, vectorResult, 5);
    }

    private RetrievalResult result(String idCharacter, double score, long durationMs) {
        DocumentChunk chunk = new DocumentChunk(
                "careerforge",
                "document-1",
                "测试文档",
                KnowledgeDocumentType.INTERVIEW_EXPERIENCE,
                "测试文档.md",
                "a".repeat(64),
                "markdown-cleaner-v2",
                "markdown-structure-v2|max=1000|overlap=120",
                idCharacter.repeat(64),
                0,
                List.of("测试文档", "RAG"),
                0,
                4,
                "测试正文"
        );
        return new RetrievalResult(List.of(new RetrievedChunk(chunk, score, 1)), durationMs);
    }
}