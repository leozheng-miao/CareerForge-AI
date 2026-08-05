package com.leo.careerforgeai.knowledge.application.indexing;

import com.leo.careerforgeai.knowledge.domain.document.CleanedDocument;
import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.indexing.KnowledgeIndexFailure;
import com.leo.careerforgeai.knowledge.domain.indexing.KnowledgeIndexResult;
import com.leo.careerforgeai.knowledge.domain.document.SourceDocument;
import com.leo.careerforgeai.knowledge.infrastructure.document.cleaning.DocumentCleaner;
import com.leo.careerforgeai.knowledge.infrastructure.document.chunking.MarkdownChunker;
import com.leo.careerforgeai.knowledge.infrastructure.document.loading.MarkdownDocumentLoader;
import com.leo.careerforgeai.model.application.EmbeddingGateway;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingPurpose;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingRequest;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-03 14:16
 **/
class KnowledgeIndexingServiceTest {

    private final MarkdownDocumentLoader documentLoader = mock(MarkdownDocumentLoader.class);
    private final DocumentCleaner documentCleaner = mock(DocumentCleaner.class);
    private final MarkdownChunker chunker = mock(MarkdownChunker.class);
    private final EmbeddingGateway embeddingGateway = mock(EmbeddingGateway.class);
    private final KnowledgeIndex knowledgeIndex = mock(KnowledgeIndex.class);
    private final KnowledgeIndexingService service = new KnowledgeIndexingService(documentLoader, documentCleaner, chunker, embeddingGateway, knowledgeIndex);

    @Test
    void shouldExecuteDocumentIndexingPipelineWithRetrievalText() {
        SourceDocument source = sourceDocument();
        CleanedDocument cleaned = new CleanedDocument(source, "markdown-cleaner-v2", "清洗正文");
        DocumentChunk chunk = chunk();
        EmbeddingResult embeddingResult = new EmbeddingResult("qwen3-embedding:0.6b", 2, List.of(List.of(0.1F, 0.2F)), 10);
        KnowledgeIndexResult expectedResult = new KnowledgeIndexResult(1, 1, List.of());

        when(documentLoader.loadAll()).thenReturn(List.of(source));
        when(documentCleaner.clean(source)).thenReturn(cleaned);
        when(chunker.chunk(cleaned)).thenReturn(List.of(chunk));
        when(embeddingGateway.embed(any(EmbeddingRequest.class))).thenReturn(embeddingResult);
        when(knowledgeIndex.index(List.of(chunk), embeddingResult)).thenReturn(expectedResult);

        KnowledgeIndexResult actualResult = service.indexConfiguredDocuments();

        assertThat(actualResult).isSameAs(expectedResult);
        verify(embeddingGateway).embed(argThat(request -> request.purpose() == EmbeddingPurpose.DOCUMENT && request.inputs().equals(List.of(chunk.retrievalText()))));
        verify(knowledgeIndex).index(List.of(chunk), embeddingResult);
        verify(knowledgeIndex).activateCurrentVersion();
    }

    @Test
    void shouldNotWriteIndexWhenEmbeddingFails() {
        SourceDocument source = sourceDocument();
        CleanedDocument cleaned = new CleanedDocument(source, "markdown-cleaner-v2", "清洗正文");
        RuntimeException embeddingFailure = new RuntimeException("Ollama unavailable");

        when(documentLoader.loadAll()).thenReturn(List.of(source));
        when(documentCleaner.clean(source)).thenReturn(cleaned);
        when(chunker.chunk(cleaned)).thenReturn(List.of(chunk()));
        when(embeddingGateway.embed(any(EmbeddingRequest.class))).thenThrow(embeddingFailure);

        assertThatThrownBy(service::indexConfiguredDocuments).isSameAs(embeddingFailure);
        verifyNoInteractions(knowledgeIndex);
    }

    @Test
    void shouldNotActivateIndexWhenBulkHasFailures() {
        SourceDocument source = sourceDocument();
        CleanedDocument cleaned = new CleanedDocument(source, "markdown-cleaner-v2", "清洗正文");
        DocumentChunk chunk = chunk();
        EmbeddingResult embeddingResult = new EmbeddingResult("qwen3-embedding:0.6b", 2, List.of(List.of(0.1F, 0.2F)), 10);
        KnowledgeIndexResult partialResult = new KnowledgeIndexResult(1, 0, List.of(new KnowledgeIndexFailure(chunk.chunkId(), 400, "mapper_parsing_exception", "向量写入失败")));

        when(documentLoader.loadAll()).thenReturn(List.of(source));
        when(documentCleaner.clean(source)).thenReturn(cleaned);
        when(chunker.chunk(cleaned)).thenReturn(List.of(chunk));
        when(embeddingGateway.embed(any(EmbeddingRequest.class))).thenReturn(embeddingResult);
        when(knowledgeIndex.index(List.of(chunk), embeddingResult)).thenReturn(partialResult);

        KnowledgeIndexResult result = service.indexConfiguredDocuments();

        assertThat(result).isSameAs(partialResult);
        verify(knowledgeIndex, never()).activateCurrentVersion();
    }

    private SourceDocument sourceDocument() {
        return new SourceDocument("careerforge", "document-1", "测试文档", KnowledgeDocumentType.JOB_DESCRIPTION, "测试文档.md", "a".repeat(64), "原始正文");
    }

    private DocumentChunk chunk() {
        return new DocumentChunk("careerforge", "document-1", "测试文档", KnowledgeDocumentType.JOB_DESCRIPTION, "测试文档.md", "a".repeat(64), "markdown-cleaner-v2", "markdown-structure-v2|max=1000|overlap=120", "b".repeat(64), 0, List.of("测试文档", "Java"), 0, 4, "清洗正文");
    }
}