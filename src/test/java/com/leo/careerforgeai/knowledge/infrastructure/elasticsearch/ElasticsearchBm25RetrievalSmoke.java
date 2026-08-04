package com.leo.careerforgeai.knowledge.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.leo.careerforgeai.knowledge.application.Bm25Retriever;
import com.leo.careerforgeai.knowledge.domain.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievedChunk;
import com.leo.careerforgeai.knowledge.infrastructure.document.KnowledgeSourceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-04 13:39
 **/
@SpringBootTest(properties = {"careerforge.model.base-url=http://localhost", "careerforge.model.api-key=smoke-test-placeholder", "careerforge.model.name=smoke-test-placeholder"})
class ElasticsearchBm25RetrievalSmoke {

    @Autowired
    private Bm25Retriever retriever;

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private KnowledgeIndexProperties indexProperties;

    @Autowired
    private KnowledgeSourceProperties sourceProperties;

    @Test
    void shouldRetrieveRealChunksWithBm25AndFilters() throws IOException {
        String alias = indexProperties.getIndexAlias();
        boolean aliasExists = client.indices().existsAlias(request -> request.name(alias)).value();
        assertThat(aliasExists).as("查询Alias必须已经发布").isTrue();

        Set<String> aliasTargets = client.indices().getAlias(request -> request.name(alias)).aliases().keySet();
        assertThat(aliasTargets).containsExactly(indexProperties.concreteIndexName());

        RetrievalScope allDocuments = new RetrievalScope(sourceProperties.getKnowledgeBaseId(), Set.of(), Set.of());
        RetrievalResult javaConcurrency = retriever.retrieve("Java 并发", allDocuments, 5);
        RetrievalResult springBoot = retriever.retrieve("Spring Boot", allDocuments, 5);
        RetrievalResult vectorDatabase = retriever.retrieve("向量数据库", allDocuments, 5);

        assertValidResult(javaConcurrency, allDocuments, 5);
        assertValidResult(springBoot, allDocuments, 5);
        assertValidResult(vectorDatabase, allDocuments, 5);
        assertThat(javaConcurrency.chunks()).isNotEmpty();
        assertThat(springBoot.chunks()).isNotEmpty();
        assertThat(vectorDatabase.chunks()).isNotEmpty();

        RetrievalScope jobDescriptions = new RetrievalScope(sourceProperties.getKnowledgeBaseId(), Set.of(KnowledgeDocumentType.JOB_DESCRIPTION), Set.of());
        RetrievalResult jobResult = retriever.retrieve("AI 应用开发", jobDescriptions, 5);
        assertThat(jobResult.chunks()).isNotEmpty();
        assertThat(jobResult.chunks()).allSatisfy(hit -> assertThat(hit.chunk().documentType()).isEqualTo(KnowledgeDocumentType.JOB_DESCRIPTION));

        RetrievalScope interviewDocument = new RetrievalScope(sourceProperties.getKnowledgeBaseId(), Set.of(), Set.of("ai-interview-summary"));
        RetrievalResult interviewResult = retriever.retrieve("RAG", interviewDocument, 5);
        assertThat(interviewResult.chunks()).isNotEmpty();
        assertThat(interviewResult.chunks()).allSatisfy(hit -> assertThat(hit.chunk().documentId()).isEqualTo("ai-interview-summary"));

        RetrievalResult noResult = retriever.retrieve("zzzzzz_no_such_term_8472", allDocuments, 5);
        assertThat(noResult.chunks()).isEmpty();

        printResult("javaConcurrency", "Java 并发", javaConcurrency);
        printResult("springBoot", "Spring Boot", springBoot);
        printResult("vectorDatabase", "向量数据库", vectorDatabase);
        printResult("jobFilter", "AI 应用开发", jobResult);
        printResult("documentFilter", "RAG", interviewResult);
        System.out.printf("noResultHits=%d%n", noResult.chunks().size());
    }

    private void assertValidResult(RetrievalResult result, RetrievalScope scope, int topK) {
        assertThat(result.chunks()).hasSizeLessThanOrEqualTo(topK);
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.chunks()).extracting(hit -> hit.chunk().chunkId()).doesNotHaveDuplicates();
        assertThat(result.chunks()).allSatisfy(hit -> assertThat(hit.chunk().knowledgeBaseId()).isEqualTo(scope.knowledgeBaseId()));
        assertThat(result.chunks()).extracting(RetrievedChunk::rank).containsExactlyElementsOf(IntStream.rangeClosed(1, result.chunks().size()).boxed().toList());

        List<Double> scores = result.chunks().stream().map(RetrievedChunk::score).toList();
        assertThat(IntStream.range(1, scores.size()).allMatch(index -> scores.get(index - 1) >= scores.get(index))).isTrue();
    }

    private void printResult(String label, String query, RetrievalResult result) {
        System.out.printf("label=%s, query=%s, hits=%d, durationMs=%d%n", label, query, result.chunks().size(), result.durationMs());
        result.chunks().forEach(hit -> System.out.printf("  rank=%d, score=%.6f, chunkId=%s, documentId=%s, sectionPath=%s, preview=%s%n", hit.rank(), hit.score(), hit.chunk().chunkId().substring(0, 12), hit.chunk().documentId(), hit.chunk().sectionPath(), preview(hit.chunk().content())));
    }

    private String preview(String content) {
        String singleLine = content.replace('\n', ' ');
        return singleLine.length() <= 120 ? singleLine : singleLine.substring(0, 120) + "...";
    }
}