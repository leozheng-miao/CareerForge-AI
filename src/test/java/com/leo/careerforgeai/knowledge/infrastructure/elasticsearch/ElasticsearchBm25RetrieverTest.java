package com.leo.careerforgeai.knowledge.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.leo.careerforgeai.knowledge.domain.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-03 22:29
 **/
class ElasticsearchBm25RetrieverTest {

    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final ElasticsearchBm25Retriever retriever = new ElasticsearchBm25Retriever(client, new KnowledgeIndexProperties("careerforge-knowledge", "v1"));

    @Test
    void shouldBuildFilteredBm25QueryAndMapRankedHits() throws IOException {
        RetrievalScope scope = new RetrievalScope("careerforge", Set.of(KnowledgeDocumentType.INTERVIEW_EXPERIENCE), Set.of("interview-document"));
        when(client.search(any(SearchRequest.class), eq(KnowledgeChunkSearchDocument.class))).thenReturn(response(hit()));

        RetrievalResult result = retriever.retrieve("Java 并发", scope, 5);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(captor.capture(), eq(KnowledgeChunkSearchDocument.class));
        SearchRequest request = captor.getValue();
        BoolQuery boolQuery = request.query().bool();

        assertThat(request.index()).containsExactly("careerforge-knowledge");
        assertThat(request.size()).isEqualTo(5);
        assertThat(request.allowPartialSearchResults()).isFalse();
        assertThat(request.source().filter().excludeVectors()).isTrue();
        assertThat(boolQuery.must()).singleElement().satisfies(query -> {
            assertThat(query.match().field()).isEqualTo("retrievalText");
            assertThat(query.match().query().stringValue()).isEqualTo("Java 并发");
        });
        assertThat(boolQuery.filter()).hasSize(3);
        assertThat(boolQuery.filter().get(0).term().field()).isEqualTo("knowledgeBaseId");
        assertThat(boolQuery.filter().get(0).term().value().stringValue()).isEqualTo("careerforge");
        assertThat(boolQuery.filter().get(1).terms().field()).isEqualTo("documentType");
        assertThat(boolQuery.filter().get(2).terms().field()).isEqualTo("documentId");

        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().getFirst().rank()).isEqualTo(1);
        assertThat(result.chunks().getFirst().score()).isEqualTo(7.5D);
        assertThat(result.chunks().getFirst().chunk().chunkId()).isEqualTo("b".repeat(64));
        assertThat(result.chunks().getFirst().chunk().documentType()).isEqualTo(KnowledgeDocumentType.INTERVIEW_EXPERIENCE);
    }

    @Test
    void shouldReturnEmptyResultWhenElasticsearchHasNoHits() throws IOException {
        when(client.search(any(SearchRequest.class), eq(KnowledgeChunkSearchDocument.class))).thenReturn(response());

        RetrievalResult result = retriever.retrieve("不存在的内容", new RetrievalScope("careerforge", Set.of(), Set.of()), 5);

        assertThat(result.chunks()).isEmpty();
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldRejectInvalidInputBeforeSearch() {
        RetrievalScope scope = new RetrievalScope("careerforge", Set.of(), Set.of());

        assertThatThrownBy(() -> retriever.retrieve(" ", scope, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retriever.retrieve("Java", scope, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retriever.retrieve("Java", scope, 101)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client);
    }

    private Hit<KnowledgeChunkSearchDocument> hit() {
        KnowledgeChunkSearchDocument source = new KnowledgeChunkSearchDocument(
                "careerforge",
                "interview-document",
                "面经",
                "INTERVIEW_EXPERIENCE",
                "面经.md",
                "a".repeat(64),
                "markdown-cleaner-v2",
                "markdown-structure-v2|max=1000|overlap=120",
                "b".repeat(64),
                0,
                java.util.List.of("面经", "Java"),
                0,
                4,
                "Java 并发"
        );
        return Hit.of(hit -> hit.index("careerforge-knowledge-v1").id(source.chunkId()).score(7.5D).source(source));
    }

    @SafeVarargs
    private final SearchResponse<KnowledgeChunkSearchDocument> response(Hit<KnowledgeChunkSearchDocument>... hits) {
        return SearchResponse.of(response -> response
                .took(3)
                .timedOut(false)
                .shards(shards -> shards.total(1).successful(1).failed(0))
                .hits(metadata -> metadata.hits(Arrays.asList(hits))));
    }
}