package com.leo.careerforgeai.knowledge.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.leo.careerforgeai.knowledge.domain.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.KnowledgeIndexResult;
import com.leo.careerforgeai.knowledge.domain.SourceDocument;
import com.leo.careerforgeai.knowledge.infrastructure.document.DocumentCleaner;
import com.leo.careerforgeai.knowledge.infrastructure.document.MarkdownChunker;
import com.leo.careerforgeai.knowledge.infrastructure.document.MarkdownDocumentLoader;
import com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.KnowledgeChunkIndexDocument;
import com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.KnowledgeIndexProperties;
import com.leo.careerforgeai.model.infrastructure.ollama.OllamaEmbeddingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description:
 *      Ollama 真实返回的 1024 维向量能被 Elasticsearch 接受。
 *      Java record 能正确序列化为 _source。
 *      43 个稳定 Chunk ID 重复写入后数量不增加。
 *      写入后能够立即读取真实正文、元数据和向量。
 * <p>
 *      空索引 → 允许第一次入库
 * <p>
 * 已有索引且 Chunk ID 集合一致 → 允许幂等覆盖
 * <p>
 * 已有索引与当前 sourceHash/chunkerVersion 生成的 ID 不一致 → 写入前失败，要求创建新索引版本
 *
 * @author: Miao Zheng
 * @date: 2026-08-03 14:30
 **/
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"careerforge.model.base-url=http://localhost", "careerforge.model.api-key=smoke-test-placeholder", "careerforge.model.name=smoke-test-placeholder"})
class KnowledgeIndexingSmoke {

    @Autowired
    private KnowledgeIndexingService indexingService;

    @Autowired
    private MarkdownDocumentLoader documentLoader;

    @Autowired
    private DocumentCleaner documentCleaner;

    @Autowired
    private MarkdownChunker chunker;

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private KnowledgeIndexProperties indexProperties;

    @Autowired
    private OllamaEmbeddingProperties embeddingProperties;

    @Test
    void shouldIndexRealCorpusIdempotently() throws IOException {
        String indexName = indexProperties.concreteIndexName();
        boolean indexExists = client.indices().exists(request -> request.index(indexName)).value();
        assertThat(indexExists).as("必须先创建具有严格 Mapping 的版本化索引").isTrue();

        List<SourceDocument> sourceDocuments = documentLoader.loadAll();
        List<DocumentChunk> expectedChunks = sourceDocuments.stream()
                .map(documentCleaner::clean)
                .flatMap(document -> chunker.chunk(document).stream())
                .toList();

        assertThat(sourceDocuments).hasSize(2);
        assertThat(expectedChunks).isNotEmpty();

        long beforeCount = count(indexName);
        assertExistingIndexMatchesCurrentChunks(indexName, expectedChunks, beforeCount);

        KnowledgeIndexResult firstResult = indexingService.indexConfiguredDocuments();
        long afterFirstCount = count(indexName);

        KnowledgeIndexResult secondResult = indexingService.indexConfiguredDocuments();
        long afterSecondCount = count(indexName);

        assertThat(firstResult.hasFailures()).isFalse();
        assertThat(firstResult.requestedCount()).isEqualTo(expectedChunks.size());
        assertThat(firstResult.indexedCount()).isEqualTo(expectedChunks.size());
        assertThat(secondResult.hasFailures()).isFalse();
        assertThat(secondResult.indexedCount()).isEqualTo(expectedChunks.size());
        assertThat(afterFirstCount).isEqualTo(expectedChunks.size());
        assertThat(afterSecondCount).isEqualTo(afterFirstCount);

        DocumentChunk sampleChunk = expectedChunks.getFirst();
        GetResponse<KnowledgeChunkIndexDocument> storedResponse = client.get(request -> request.index(indexName).id(sampleChunk.chunkId()).sourceExcludeVectors(false), KnowledgeChunkIndexDocument.class);

        assertThat(storedResponse.found()).isTrue();
        assertThat(storedResponse.source()).isNotNull();

        KnowledgeChunkIndexDocument storedDocument = storedResponse.source();
        assertThat(storedDocument.chunkId()).isEqualTo(sampleChunk.chunkId());
        assertThat(storedDocument.sourceHash()).isEqualTo(sampleChunk.sourceHash());
        assertThat(storedDocument.content()).isEqualTo(sampleChunk.content());
        assertThat(storedDocument.retrievalText()).isEqualTo(sampleChunk.retrievalText());
        assertThat(storedDocument.embeddingModel()).isEqualTo(embeddingProperties.getModel());
        assertThat(storedDocument.embeddingDimensions()).isEqualTo(embeddingProperties.getDimensions());
        assertThat(storedDocument.embedding()).hasSize(embeddingProperties.getDimensions());

        System.out.printf("index=%s, documents=%d, expectedChunks=%d, beforeCount=%d, afterFirstCount=%d, afterSecondCount=%d, firstFailed=%d, secondFailed=%d%n", indexName, sourceDocuments.size(), expectedChunks.size(), beforeCount, afterFirstCount, afterSecondCount, firstResult.failedCount(), secondResult.failedCount());
        System.out.printf("sampleChunkId=%s, documentId=%s, sourceHash=%s, retrievalChars=%d, embeddingModel=%s, embeddingDimensions=%d%n", storedDocument.chunkId().substring(0, 12), storedDocument.documentId(), storedDocument.sourceHash(), storedDocument.retrievalText().length(), storedDocument.embeddingModel(), storedDocument.embeddingDimensions());
    }

    @Test
    void shouldReadStoredVectorResponse() throws IOException {
        String indexName = indexProperties.concreteIndexName();
        DocumentChunk sampleChunk = documentLoader.loadAll().stream()
                .map(documentCleaner::clean)
                .flatMap(document -> chunker.chunk(document).stream())
                .findFirst()
                .orElseThrow();

        GetResponse<KnowledgeChunkIndexDocument> response = client.get(
                request -> request.index(indexName).id(sampleChunk.chunkId()).sourceExcludeVectors(false),
                KnowledgeChunkIndexDocument.class
        );

        assertThat(response.found()).isTrue();
        assertThat(response.source()).isNotNull();
        assertThat(response.source().embedding()).hasSize(embeddingProperties.getDimensions());
        assertThat(response.source().embeddingDimensions()).isEqualTo(embeddingProperties.getDimensions());
    }

    private long count(String indexName) throws IOException {
        return client.count(request -> request.index(indexName)).count();
    }

    /** 在写入前阻止当前语料与目标索引中的旧版本 Chunk 混合。 */
    private void assertExistingIndexMatchesCurrentChunks(String indexName, List<DocumentChunk> expectedChunks, long existingCount) throws IOException {
        if (existingCount == 0) return;

        assertThat(existingCount).as("已有索引文档数必须与当前 Chunk 数一致，否则应更换索引版本并重建").isEqualTo(expectedChunks.size());
        for (DocumentChunk chunk : expectedChunks) {
            boolean exists = client.exists(request -> request.index(indexName).id(chunk.chunkId())).value();
            assertThat(exists).as("已有索引必须包含当前稳定 Chunk ID：%s", chunk.chunkId()).isTrue();
        }
    }
}