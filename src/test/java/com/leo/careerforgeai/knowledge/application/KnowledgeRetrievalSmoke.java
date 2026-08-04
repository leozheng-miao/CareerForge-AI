package com.leo.careerforgeai.knowledge.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.leo.careerforgeai.knowledge.domain.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalComparisonResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievedChunk;
import com.leo.careerforgeai.knowledge.infrastructure.document.KnowledgeSourceProperties;
import com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.KnowledgeIndexProperties;
import com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.KnowledgeRetrievalException;
import com.leo.careerforgeai.model.infrastructure.ollama.OllamaEmbeddingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 使用真实 Ollama 和 Elasticsearch 验证 BM25、Query Embedding、kNN 及元数据过滤完整链路
 * @author: Miao Zheng
 * @date: 2026-08-04
 **/
@SpringBootTest(properties = {"careerforge.model.base-url=http://localhost", "careerforge.model.api-key=smoke-test-placeholder", "careerforge.model.name=smoke-test-placeholder"})
class KnowledgeRetrievalSmoke {

    private static final int TOP_K = 5;
    private static final int NUM_CANDIDATES = 50;

    @Autowired
    private KnowledgeRetrievalService retrievalService;

    @Autowired
    private VectorRetriever vectorRetriever;

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private KnowledgeIndexProperties indexProperties;

    @Autowired
    private KnowledgeSourceProperties sourceProperties;

    @Autowired
    private OllamaEmbeddingProperties embeddingProperties;

    /** 验证真实双路召回、过滤、空结果和错误向量维度。 */
    @Test
    void shouldRetrieveRealBm25AndVectorResults() throws IOException {
        String alias = indexProperties.getIndexAlias();
        assertThat(client.indices().existsAlias(request -> request.name(alias)).value()).as("查询 Alias 必须已经发布").isTrue();

        RetrievalScope allDocuments = new RetrievalScope(sourceProperties.getKnowledgeBaseId(), Set.of(), Set.of());
        RetrievalComparisonResult exactQuery = retrievalService.retrieveBoth("Java 并发", allDocuments, TOP_K, NUM_CANDIDATES);
        RetrievalComparisonResult semanticQuery = retrievalService.retrieveBoth("怎样找到措辞不同但含义相近的技术资料", allDocuments, TOP_K, NUM_CANDIDATES);

        assertValidComparison(exactQuery, allDocuments);
        assertValidComparison(semanticQuery, allDocuments);
        assertThat(exactQuery.bm25Result().chunks()).isNotEmpty();
        assertThat(exactQuery.vectorResult().chunks()).isNotEmpty();
        assertThat(semanticQuery.vectorResult().chunks()).isNotEmpty();

        RetrievalScope jobDescriptions = new RetrievalScope(sourceProperties.getKnowledgeBaseId(), Set.of(KnowledgeDocumentType.JOB_DESCRIPTION), Set.of());
        RetrievalComparisonResult filteredResult = retrievalService.retrieveBoth("AI 应用开发", jobDescriptions, TOP_K, NUM_CANDIDATES);

        assertValidComparison(filteredResult, jobDescriptions);
        assertThat(filteredResult.bm25Result().chunks()).isNotEmpty();
        assertThat(filteredResult.vectorResult().chunks()).isNotEmpty();

        RetrievalScope missingDocument = new RetrievalScope(sourceProperties.getKnowledgeBaseId(), Set.of(), Set.of("missing-document"));
        RetrievalComparisonResult emptyResult = retrievalService.retrieveBoth("RAG", missingDocument, TOP_K, NUM_CANDIDATES);

        assertThat(emptyResult.bm25Result().chunks()).isEmpty();
        assertThat(emptyResult.vectorResult().chunks()).isEmpty();

        assertThatThrownBy(() -> vectorRetriever.retrieve(List.of(0.1F, 0.2F), allDocuments, TOP_K, NUM_CANDIDATES))
                .isInstanceOf(KnowledgeRetrievalException.class);

        printComparison("exact", "Java 并发", exactQuery);
        printComparison("semantic", "怎样找到措辞不同但含义相近的技术资料", semanticQuery);
        printComparison("jobFilter", "AI 应用开发", filteredResult);
        System.out.printf("emptyFilterBm25Hits=%d, emptyFilterVectorHits=%d%n", emptyResult.bm25Result().chunks().size(), emptyResult.vectorResult().chunks().size());
    }

    private void assertValidComparison(RetrievalComparisonResult result, RetrievalScope scope) {
        assertThat(result.embeddingModel()).isEqualTo(embeddingProperties.getModel());
        assertThat(result.embeddingDimensions()).isEqualTo(embeddingProperties.getDimensions());
        assertThat(result.queryEmbeddingDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.vectorTotalDurationMs()).isEqualTo(result.queryEmbeddingDurationMs() + result.vectorResult().durationMs());
        assertValidRoute(result.bm25Result(), scope);
        assertValidRoute(result.vectorResult(), scope);
    }

    private void assertValidRoute(RetrievalResult result, RetrievalScope scope) {
        assertThat(result.chunks()).hasSizeLessThanOrEqualTo(TOP_K);
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.chunks()).extracting(hit -> hit.chunk().chunkId()).doesNotHaveDuplicates();
        assertThat(result.chunks()).extracting(RetrievedChunk::rank).containsExactlyElementsOf(IntStream.rangeClosed(1, result.chunks().size()).boxed().toList());
        assertThat(result.chunks()).allSatisfy(hit -> {
            assertThat(hit.chunk().knowledgeBaseId()).isEqualTo(scope.knowledgeBaseId());
            if (!scope.documentTypes().isEmpty()) assertThat(scope.documentTypes()).contains(hit.chunk().documentType());
            if (!scope.documentIds().isEmpty()) assertThat(scope.documentIds()).contains(hit.chunk().documentId());
        });

        List<Double> scores = result.chunks().stream().map(RetrievedChunk::score).toList();
        assertThat(IntStream.range(1, scores.size()).allMatch(index -> scores.get(index - 1) >= scores.get(index))).isTrue();
    }

    private void printComparison(String label, String query, RetrievalComparisonResult result) {
        System.out.printf("label=%s, query=%s, model=%s, dimensions=%d, queryEmbeddingMs=%d, bm25Ms=%d, vectorSearchMs=%d%n", label, query, result.embeddingModel(), result.embeddingDimensions(), result.queryEmbeddingDurationMs(), result.bm25Result().durationMs(), result.vectorResult().durationMs());
        printRoute("BM25", result.bm25Result());
        printRoute("VECTOR", result.vectorResult());
    }

    private void printRoute(String route, RetrievalResult result) {
        System.out.printf("  route=%s, hits=%d%n", route, result.chunks().size());
        result.chunks().forEach(hit -> System.out.printf("    rank=%d, score=%.6f, chunkId=%s, documentId=%s, sectionPath=%s, preview=%s%n", hit.rank(), hit.score(), hit.chunk().chunkId().substring(0, 12), hit.chunk().documentId(), hit.chunk().sectionPath(), preview(hit.chunk().content())));
    }

    private String preview(String content) {
        String singleLine = content.replace('\n', ' ');
        return singleLine.length() <= 120 ? singleLine : singleLine.substring(0, 120) + "...";
    }
}