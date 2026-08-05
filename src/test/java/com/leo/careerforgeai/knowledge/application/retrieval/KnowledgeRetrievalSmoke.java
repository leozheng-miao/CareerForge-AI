package com.leo.careerforgeai.knowledge.application.retrieval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.retrieval.HybridRetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalComparisonResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievedChunk;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import com.leo.careerforgeai.knowledge.config.KnowledgeSourceProperties;
import com.leo.careerforgeai.knowledge.config.KnowledgeIndexProperties;
import com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.retrieval.KnowledgeRetrievalException;
import com.leo.careerforgeai.model.infrastructure.ollama.OllamaEmbeddingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.HashSet;
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

    private static final int FINAL_TOP_K = 5;
    private static final int NUM_CANDIDATES = 50;
    private static final int CANDIDATE_TOP_K = 10;

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

    /** 验证真实双路召回、RRF 融合、过滤、空结果和错误向量维度。 */
    @Test
    void shouldRetrieveRealHybridResultsAndFilters() throws IOException {
        String alias = indexProperties.getIndexAlias();
        assertThat(client.indices().existsAlias(request -> request.name(alias)).value()).as("查询 Alias 必须已经发布").isTrue();

        RetrievalScope allDocuments = new RetrievalScope(sourceProperties.getKnowledgeBaseId(), Set.of(), Set.of());
        HybridRetrievalResult exactQuery = retrievalService.retrieveHybrid("Java 并发", allDocuments, CANDIDATE_TOP_K, NUM_CANDIDATES, FINAL_TOP_K);
        HybridRetrievalResult semanticQuery = retrievalService.retrieveHybrid("怎样找到措辞不同但含义相近的技术资料", allDocuments, CANDIDATE_TOP_K, NUM_CANDIDATES, FINAL_TOP_K);

        assertValidHybridResult(exactQuery, allDocuments);
        assertValidHybridResult(semanticQuery, allDocuments);
        assertThat(exactQuery.rrfChunks()).isNotEmpty();
        assertThat(semanticQuery.rrfChunks()).isNotEmpty();

        RetrievalScope jobDescriptions = new RetrievalScope(sourceProperties.getKnowledgeBaseId(), Set.of(KnowledgeDocumentType.JOB_DESCRIPTION), Set.of());
        HybridRetrievalResult filteredResult = retrievalService.retrieveHybrid("AI 应用开发", jobDescriptions, CANDIDATE_TOP_K, NUM_CANDIDATES, FINAL_TOP_K);

        assertValidHybridResult(filteredResult, jobDescriptions);
        assertThat(filteredResult.rrfChunks()).isNotEmpty();
        assertThat(filteredResult.rrfChunks()).allSatisfy(item -> assertThat(item.chunk().documentType()).isEqualTo(KnowledgeDocumentType.JOB_DESCRIPTION));

        RetrievalScope missingDocument = new RetrievalScope(sourceProperties.getKnowledgeBaseId(), Set.of(), Set.of("missing-document"));
        HybridRetrievalResult emptyResult = retrievalService.retrieveHybrid("RAG", missingDocument, CANDIDATE_TOP_K, NUM_CANDIDATES, FINAL_TOP_K);

        assertThat(emptyResult.comparisonResult().bm25Result().chunks()).isEmpty();
        assertThat(emptyResult.comparisonResult().vectorResult().chunks()).isEmpty();
        assertThat(emptyResult.rrfChunks()).isEmpty();

        assertThatThrownBy(() -> vectorRetriever.retrieve(List.of(0.1F, 0.2F), allDocuments, FINAL_TOP_K, NUM_CANDIDATES))
                .isInstanceOf(KnowledgeRetrievalException.class);

        printHybridResult("exact", "Java 并发", exactQuery);
        printHybridResult("semantic", "怎样找到措辞不同但含义相近的技术资料", semanticQuery);
        printHybridResult("jobFilter", "AI 应用开发", filteredResult);
        System.out.printf("emptyFilterBm25Hits=%d, emptyFilterVectorHits=%d, emptyFilterRrfHits=%d%n", emptyResult.comparisonResult().bm25Result().chunks().size(), emptyResult.comparisonResult().vectorResult().chunks().size(), emptyResult.rrfChunks().size());
    }

    /** 验证双路结果和 RRF 最终结果之间的排名引用关系。 */
    private void assertValidHybridResult(HybridRetrievalResult result, RetrievalScope scope) {
        RetrievalComparisonResult comparison = result.comparisonResult();
        assertValidComparison(comparison, scope);
        assertThat(result.fusionDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.rrfChunks()).hasSizeLessThanOrEqualTo(FINAL_TOP_K);
        assertThat(result.rrfChunks()).extracting(item -> item.chunk().chunkId()).doesNotHaveDuplicates();
        assertThat(result.rrfChunks()).extracting(RrfRankedChunk::finalRank).containsExactlyElementsOf(IntStream.rangeClosed(1, result.rrfChunks().size()).boxed().toList());

        Set<String> candidateIds = new HashSet<>();
        comparison.bm25Result().chunks().forEach(item -> candidateIds.add(item.chunk().chunkId()));
        comparison.vectorResult().chunks().forEach(item -> candidateIds.add(item.chunk().chunkId()));

        result.rrfChunks().forEach(item -> {
            assertThat(candidateIds).contains(item.chunk().chunkId());
            assertThat(item.chunk().knowledgeBaseId()).isEqualTo(scope.knowledgeBaseId());
            assertThat(item.rrfScore()).isPositive();

            if (item.bm25Rank() != null) {
                RetrievedChunk bm25Chunk = comparison.bm25Result().chunks().get(item.bm25Rank() - 1);
                assertThat(bm25Chunk.chunk().chunkId()).isEqualTo(item.chunk().chunkId());
            }
            if (item.vectorRank() != null) {
                RetrievedChunk vectorChunk = comparison.vectorResult().chunks().get(item.vectorRank() - 1);
                assertThat(vectorChunk.chunk().chunkId()).isEqualTo(item.chunk().chunkId());
            }
        });

        List<Double> rrfScores = result.rrfChunks().stream().map(RrfRankedChunk::rrfScore).toList();
        assertThat(IntStream.range(1, rrfScores.size()).allMatch(index -> rrfScores.get(index - 1) >= rrfScores.get(index))).isTrue();
    }

    /** 验证真实 Embedding 信息以及两条召回路线的基础约束。 */
    private void assertValidComparison(RetrievalComparisonResult result, RetrievalScope scope) {
        assertThat(result.embeddingModel()).isEqualTo(embeddingProperties.getModel());
        assertThat(result.embeddingDimensions()).isEqualTo(embeddingProperties.getDimensions());
        assertThat(result.queryEmbeddingDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.vectorTotalDurationMs()).isEqualTo(result.queryEmbeddingDurationMs() + result.vectorResult().durationMs());
        assertValidRoute(result.bm25Result(), scope);
        assertValidRoute(result.vectorResult(), scope);
    }

    /** 验证单条召回路线的数量、顺序、去重和数据隔离约束。 */
    private void assertValidRoute(RetrievalResult result, RetrievalScope scope) {
        assertThat(result.chunks()).hasSizeLessThanOrEqualTo(CANDIDATE_TOP_K);
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.chunks()).extracting(item -> item.chunk().chunkId()).doesNotHaveDuplicates();
        assertThat(result.chunks()).extracting(RetrievedChunk::rank).containsExactlyElementsOf(IntStream.rangeClosed(1, result.chunks().size()).boxed().toList());
        assertThat(result.chunks()).allSatisfy(item -> {
            assertThat(item.chunk().knowledgeBaseId()).isEqualTo(scope.knowledgeBaseId());
            if (!scope.documentTypes().isEmpty()) assertThat(scope.documentTypes()).contains(item.chunk().documentType());
            if (!scope.documentIds().isEmpty()) assertThat(scope.documentIds()).contains(item.chunk().documentId());
        });

        List<Double> scores = result.chunks().stream().map(RetrievedChunk::score).toList();
        assertThat(IntStream.range(1, scores.size()).allMatch(index -> scores.get(index - 1) >= scores.get(index))).isTrue();
    }

    /** 输出 BM25、Vector 和最终 RRF 三组排名用于人工对照。 */
    private void printHybridResult(String label, String query, HybridRetrievalResult result) {
        RetrievalComparisonResult comparison = result.comparisonResult();
        System.out.printf("label=%s, query=%s, model=%s, dimensions=%d, queryEmbeddingMs=%d, bm25Ms=%d, vectorSearchMs=%d, fusionMs=%d%n", label, query, comparison.embeddingModel(), comparison.embeddingDimensions(), comparison.queryEmbeddingDurationMs(), comparison.bm25Result().durationMs(), comparison.vectorResult().durationMs(), result.fusionDurationMs());
        printRoute("BM25", comparison.bm25Result());
        printRoute("VECTOR", comparison.vectorResult());
        printRrfRoute(result.rrfChunks());
    }

    /** 输出一条原始召回路线。 */
    private void printRoute(String route, RetrievalResult result) {
        System.out.printf("  route=%s, hits=%d%n", route, result.chunks().size());
        result.chunks().forEach(item -> System.out.printf("    rank=%d, score=%.6f, chunkId=%s, documentId=%s, sectionPath=%s, preview=%s%n", item.rank(), item.score(), item.chunk().chunkId().substring(0, 12), item.chunk().documentId(), item.chunk().sectionPath(), preview(item.chunk().content())));
    }

    /** 输出 RRF 最终排名及其两路排名依据。 */
    private void printRrfRoute(List<RrfRankedChunk> chunks) {
        System.out.printf("  route=RRF, hits=%d%n", chunks.size());
        chunks.forEach(item -> {
            String bm25Rank = item.bm25Rank() == null ? "-" : item.bm25Rank().toString();
            String vectorRank = item.vectorRank() == null ? "-" : item.vectorRank().toString();
            System.out.printf("    finalRank=%d, rrfScore=%.8f, bm25Rank=%s, vectorRank=%s, chunkId=%s, documentId=%s, sectionPath=%s, preview=%s%n", item.finalRank(), item.rrfScore(), bm25Rank, vectorRank, item.chunk().chunkId().substring(0, 12), item.chunk().documentId(), item.chunk().sectionPath(), preview(item.chunk().content()));
        });
    }

    private String preview(String content) {
        String singleLine = content.replace('\n', ' ');
        return singleLine.length() <= 120 ? singleLine : singleLine.substring(0, 120) + "...";
    }
}