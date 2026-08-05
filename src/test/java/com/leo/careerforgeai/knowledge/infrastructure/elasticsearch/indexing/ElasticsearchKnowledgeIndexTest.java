package com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.indexing;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import com.leo.careerforgeai.knowledge.config.KnowledgeIndexProperties;
import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.indexing.KnowledgeIndexResult;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesResponse;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.elasticsearch.indices.ExistsAliasRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.util.ObjectBuilder;
import org.mockito.ArgumentMatchers;

import java.util.function.Function;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-03 14:06
 **/
class ElasticsearchKnowledgeIndexTest {

    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final ElasticsearchKnowledgeIndex knowledgeIndex = new ElasticsearchKnowledgeIndex(client, new KnowledgeIndexProperties("careerforge-knowledge", "v1"));

    @Test
    void shouldBuildAlignedIdempotentBulkRequest() throws IOException {
        List<DocumentChunk> chunks = List.of(chunk(0), chunk(1));
        EmbeddingResult embeddings = embeddings(List.of(List.of(0.1F, 0.2F), List.of(0.3F, 0.4F)));
        when(client.bulk(any(BulkRequest.class))).thenReturn(response(false, success(chunks.get(0)), success(chunks.get(1))));

        KnowledgeIndexResult result = knowledgeIndex.index(chunks, embeddings);

        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client).bulk(captor.capture());
        BulkRequest request = captor.getValue();

        assertThat(request.index()).isEqualTo("careerforge-knowledge-v1");
        assertThat(request.refresh()).isEqualTo(Refresh.WaitFor);
        assertThat(request.operations()).hasSize(2);
        assertThat(request.operations()).allSatisfy(operation -> assertThat(operation.isIndex()).isTrue());
        assertThat(request.operations().get(0).index().id()).isEqualTo(chunks.get(0).chunkId());
        assertThat(request.operations().get(1).index().id()).isEqualTo(chunks.get(1).chunkId());

        KnowledgeChunkIndexDocument firstDocument = (KnowledgeChunkIndexDocument) request.operations().get(0).index().document();
        assertThat(firstDocument.chunkId()).isEqualTo(chunks.get(0).chunkId());
        assertThat(firstDocument.embedding()).containsExactly(0.1F, 0.2F);
        assertThat(firstDocument.retrievalText()).isEqualTo(chunks.get(0).retrievalText());

        assertThat(result.requestedCount()).isEqualTo(2);
        assertThat(result.indexedCount()).isEqualTo(2);
        assertThat(result.hasFailures()).isFalse();
    }

    @Test
    void shouldReturnBulkPartialFailure() throws IOException {
        List<DocumentChunk> chunks = List.of(chunk(0), chunk(1));
        when(client.bulk(any(BulkRequest.class))).thenReturn(response(true, success(chunks.get(0)), failure(chunks.get(1))));

        KnowledgeIndexResult result = knowledgeIndex.index(chunks, embeddings(List.of(List.of(0.1F, 0.2F), List.of(0.3F, 0.4F))));

        assertThat(result.indexedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures().get(0).chunkId()).isEqualTo(chunks.get(1).chunkId());
        assertThat(result.failures().get(0).status()).isEqualTo(400);
        assertThat(result.failures().get(0).errorType()).isEqualTo("mapper_parsing_exception");
        assertThat(result.failures().get(0).reason()).isEqualTo("向量维度错误");
    }

    @Test
    void shouldRejectCountMismatchBeforeBulkRequest() {
        List<DocumentChunk> chunks = List.of(chunk(0), chunk(1));
        EmbeddingResult embeddings = embeddings(List.of(List.of(0.1F, 0.2F)));

        assertThatThrownBy(() -> knowledgeIndex.index(chunks, embeddings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Chunk数量必须等于向量数量");

        verifyNoInteractions(client);
    }

    @Test
    void shouldWrapBulkTransportFailure() throws IOException {
        when(client.bulk(any(BulkRequest.class))).thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> knowledgeIndex.index(List.of(chunk(0)), embeddings(List.of(List.of(0.1F, 0.2F)))))
                .isInstanceOf(KnowledgeIndexException.class)
                .hasMessageContaining("Elasticsearch Bulk请求失败");
    }

    @Test
    void shouldAtomicallySwitchAliasFromPreviousIndex() throws IOException {
        ElasticsearchIndicesClient indicesClient = mock(ElasticsearchIndicesClient.class);
        GetAliasResponse aliasResponse = GetAliasResponse.of(response -> response.aliases(
                "careerforge-knowledge-v0",
                index -> index.aliases("careerforge-knowledge", alias -> alias)
        ));

        when(client.indices()).thenReturn(indicesClient);
        when(indicesClient.existsAlias(ArgumentMatchers.<Function<ExistsAliasRequest.Builder, ObjectBuilder<ExistsAliasRequest>>>any()))
                .thenReturn(new BooleanResponse(true));

        when(indicesClient.getAlias(ArgumentMatchers.<Function<GetAliasRequest.Builder, ObjectBuilder<GetAliasRequest>>>any()))
                .thenReturn(aliasResponse);
        when(indicesClient.updateAliases(any(UpdateAliasesRequest.class))).thenReturn(UpdateAliasesResponse.of(response -> response.acknowledged(true)));

        knowledgeIndex.activateCurrentVersion();

        ArgumentCaptor<UpdateAliasesRequest> captor = ArgumentCaptor.forClass(UpdateAliasesRequest.class);
        verify(indicesClient).updateAliases(captor.capture());

        UpdateAliasesRequest request = captor.getValue();
        assertThat(request.actions()).hasSize(2);
        assertThat(request.actions().get(0).isRemove()).isTrue();
        assertThat(request.actions().get(0).remove().index()).isEqualTo("careerforge-knowledge-v0");
        assertThat(request.actions().get(0).remove().alias()).isEqualTo("careerforge-knowledge");
        assertThat(request.actions().get(1).isAdd()).isTrue();
        assertThat(request.actions().get(1).add().index()).isEqualTo("careerforge-knowledge-v1");
        assertThat(request.actions().get(1).add().alias()).isEqualTo("careerforge-knowledge");
    }

    private EmbeddingResult embeddings(List<List<Float>> vectors) {
        return new EmbeddingResult("qwen3-embedding:0.6b", 2, vectors, 10);
    }

    private DocumentChunk chunk(int index) {
        String chunkId = (index == 0 ? "a" : "b").repeat(64);
        return new DocumentChunk("careerforge", "document-1", "测试文档", KnowledgeDocumentType.JOB_DESCRIPTION, "测试文档.md", "f".repeat(64), "markdown-cleaner-v2", "markdown-structure-v2|max=1000|overlap=120", chunkId, index, List.of("测试文档", "章节" + index), index * 10, index * 10 + 5, "正文" + index);
    }

    private BulkResponseItem success(DocumentChunk chunk) {
        return BulkResponseItem.of(item -> item.operationType(OperationType.Index).id(chunk.chunkId()).index("careerforge-knowledge-v1").status(201).result("created"));
    }

    private BulkResponseItem failure(DocumentChunk chunk) {
        return BulkResponseItem.of(item -> item.operationType(OperationType.Index).id(chunk.chunkId()).index("careerforge-knowledge-v1").status(400).error(error -> error.type("mapper_parsing_exception").reason("向量维度错误")));
    }

    private BulkResponse response(boolean errors, BulkResponseItem... items) {
        return BulkResponse.of(response -> response.errors(errors).items(List.of(items)).took(3));
    }
}