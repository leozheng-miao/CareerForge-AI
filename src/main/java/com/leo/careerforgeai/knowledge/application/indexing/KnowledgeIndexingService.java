package com.leo.careerforgeai.knowledge.application.indexing;

import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.indexing.KnowledgeIndexResult;
import com.leo.careerforgeai.knowledge.domain.document.SourceDocument;
import com.leo.careerforgeai.knowledge.infrastructure.document.cleaning.DocumentCleaner;
import com.leo.careerforgeai.knowledge.infrastructure.document.chunking.MarkdownChunker;
import com.leo.careerforgeai.knowledge.infrastructure.document.loading.MarkdownDocumentLoader;
import com.leo.careerforgeai.model.application.EmbeddingGateway;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingPurpose;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingRequest;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 编排配置知识文档从读取到 Elasticsearch 入库的完整索引链路
 * @author: Miao Zheng
 * @date: 2026-08-03 13:53
 **/
@Service
@Slf4j
public class KnowledgeIndexingService {

    private final MarkdownDocumentLoader documentLoader;
    private final DocumentCleaner documentCleaner;
    private final MarkdownChunker chunker;
    private final EmbeddingGateway embeddingGateway;
    private final KnowledgeIndex knowledgeIndex;

    public KnowledgeIndexingService(MarkdownDocumentLoader documentLoader, DocumentCleaner documentCleaner, MarkdownChunker chunker, EmbeddingGateway embeddingGateway, KnowledgeIndex knowledgeIndex) {
        this.documentLoader = documentLoader;
        this.documentCleaner = documentCleaner;
        this.chunker = chunker;
        this.embeddingGateway = embeddingGateway;
        this.knowledgeIndex = knowledgeIndex;
    }

    /** 将配置白名单中的原始文档完整处理并写入知识索引。 */
    public KnowledgeIndexResult indexConfiguredDocuments() {
        long startNanos = System.nanoTime();

        List<SourceDocument> sourceDocuments = documentLoader.loadAll();
        List<DocumentChunk> chunks = sourceDocuments.stream()
                .map(documentCleaner::clean)
                .flatMap(document -> chunker.chunk(document).stream())
                .toList();

        List<String> embeddingInputs = chunks.stream().map(DocumentChunk::retrievalText).toList();
        EmbeddingResult embeddingResult = embeddingGateway.embed(new EmbeddingRequest(EmbeddingPurpose.DOCUMENT, embeddingInputs));
        KnowledgeIndexResult indexResult = knowledgeIndex.index(chunks, embeddingResult);
        if (!indexResult.hasFailures()) knowledgeIndex.activateCurrentVersion();

        long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        log.info("知识索引链路执行完成，documents={}, chunks={}, indexed={}, failed={}, durationMs={}", sourceDocuments.size(), chunks.size(), indexResult.indexedCount(), indexResult.failedCount(), durationMs);
        return indexResult;
    }
}