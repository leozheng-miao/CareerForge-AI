package com.leo.careerforgeai.knowledge.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnSearch;
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
import java.util.List;
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
 * @date: 2026-08-04 15:07
 **/
class ElasticsearchVectorRetrieverTest {

    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final ElasticsearchVectorRetriever retriever = new ElasticsearchVectorRetriever(client, new KnowledgeIndexProperties("careerforge-knowledge", "v1"));

    @Test
    void shouldBuildFilteredKnnQueryAndMapRankedHits() throws IOException {
        List<Float> queryVector = List.of(0.1F, 0.2F, 0.3F);
        RetrievalScope scope = new RetrievalScope("careerforge", Set.of(KnowledgeDocumentType.INTERVIEW_EXPERIENCE), Set.of("interview-document"));
        when(client.search(any(SearchRequest.class), eq(KnowledgeChunkSearchDocument.class))).thenReturn(response(hit()));

        RetrievalResult result = retriever.retrieve(queryVector, scope, 5, 50);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(captor.capture(), eq(KnowledgeChunkSearchDocument.class));
        SearchRequest request = captor.getValue();
        KnnSearch knn = request.knn().getFirst();

        assertThat(request.index()).containsExactly("careerforge-knowledge");
        assertThat(request.size()).isEqualTo(5);
        assertThat(request.allowPartialSearchResults()).isFalse();
        assertThat(request.source().filter().excludeVectors()).isTrue();
        assertThat(knn.field()).isEqualTo("embedding");
        assertThat(knn.queryVector()).containsExactlyElementsOf(queryVector);
        assertThat(knn.k()).isEqualTo(5);
        assertThat(knn.numCandidates()).isEqualTo(50);
        assertThat(knn.filter()).hasSize(3);
        assertThat(knn.filter().get(0).term().field()).isEqualTo("knowledgeBaseId");
        assertThat(knn.filter().get(0).term().value().stringValue()).isEqualTo("careerforge");
        assertThat(knn.filter().get(1).terms().field()).isEqualTo("documentType");
        assertThat(knn.filter().get(2).terms().field()).isEqualTo("documentId");

        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().getFirst().rank()).isEqualTo(1);
        assertThat(result.chunks().getFirst().score()).isEqualTo(0.91D);
        assertThat(result.chunks().getFirst().chunk().chunkId()).isEqualTo("b".repeat(64));
    }

    @Test
    void shouldReturnEmptyResultWhenKnnHasNoHits() throws IOException {
        when(client.search(any(SearchRequest.class), eq(KnowledgeChunkSearchDocument.class))).thenReturn(response());

        RetrievalResult result = retriever.retrieve(List.of(0.1F, 0.2F), new RetrievalScope("careerforge", Set.of(), Set.of()), 5, 50);

        assertThat(result.chunks()).isEmpty();
    }

    @Test
    void shouldRejectInvalidInputBeforeSearch() {
        RetrievalScope scope = new RetrievalScope("careerforge", Set.of(), Set.of());

        assertThatThrownBy(() -> retriever.retrieve(List.of(), scope, 5, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retriever.retrieve(List.of(Float.NaN), scope, 5, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retriever.retrieve(List.of(0.1F), scope, 0, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retriever.retrieve(List.of(0.1F), scope, 10, 5)).isInstanceOf(IllegalArgumentException.class);
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
                List.of("面经", "向量检索"),
                0,
                4,
                "语义相似问题"
        );
        return Hit.of(hit -> hit.index("careerforge-knowledge-v1").id(source.chunkId()).score(0.91D).source(source));
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