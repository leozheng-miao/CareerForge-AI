package com.leo.careerforgeai.knowledge.evaluation.dataset;

import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.document.SourceDocument;
import com.leo.careerforgeai.knowledge.config.ChunkingProperties;
import com.leo.careerforgeai.knowledge.infrastructure.document.cleaning.DocumentCleaner;
import com.leo.careerforgeai.knowledge.infrastructure.document.chunking.MarkdownChunker;
import com.leo.careerforgeai.knowledge.infrastructure.document.loading.MarkdownDocumentLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "careerforge.model.base-url=https://example.invalid",
        "careerforge.model.api-key=smoke-test-key",
        "careerforge.model.name=smoke-test-model"
})
class EvaluationChunkCatalogSmoke {

    private static final Path OUTPUT_PATH = Path.of("target/rag-evaluation/chunk-catalog.json");

    @Autowired
    private MarkdownDocumentLoader documentLoader;

    @Autowired
    private DocumentCleaner documentCleaner;

    @Autowired
    private MarkdownChunker markdownChunker;

    @Autowired
    private ChunkingProperties chunkingProperties;

    @Autowired
    private JsonMapper jsonMapper;

    /** 校验语料快照后生成供人工评测标注使用的完整 Chunk 目录。 */
    @Test
    void shouldGenerateCurrentChunkCatalog() throws IOException {
        EvaluationCorpusGuard corpusGuard = new EvaluationCorpusGuard(jsonMapper);
        EvaluationCorpusManifest manifest = corpusGuard.loadManifest();
        List<SourceDocument> sourceDocuments = documentLoader.loadAll();

        corpusGuard.verify(manifest, sourceDocuments, DocumentCleaner.CLEANING_VERSION, chunkingProperties.chunkerVersion());

        List<DocumentChunk> chunks = sourceDocuments.stream()
                .map(documentCleaner::clean)
                .flatMap(cleanedDocument -> markdownChunker.chunk(cleanedDocument).stream())
                .toList();

        assertThat(chunks).hasSize(43);
        assertThat(chunks).extracting(DocumentChunk::chunkId).doesNotHaveDuplicates();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.cleaningVersion()).isEqualTo(DocumentCleaner.CLEANING_VERSION);
            assertThat(chunk.chunkerVersion()).isEqualTo(chunkingProperties.chunkerVersion());
        });

        ChunkCatalog catalog = new ChunkCatalog(
                "rag-evaluation-chunk-catalog-v1",
                manifest.knowledgeBaseId(),
                DocumentCleaner.CLEANING_VERSION,
                chunkingProperties.chunkerVersion(),
                chunks.stream().map(ChunkCatalogEntry::from).toList()
        );

        Files.createDirectories(OUTPUT_PATH.getParent());
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(OUTPUT_PATH.toFile(), catalog);

        System.out.printf(
                "catalog=%s, documents=%d, chunks=%d, cleaningVersion=%s, chunkerVersion=%s%n",
                OUTPUT_PATH.toAbsolutePath(),
                sourceDocuments.size(),
                chunks.size(),
                DocumentCleaner.CLEANING_VERSION,
                chunkingProperties.chunkerVersion()
        );
    }

    private record ChunkCatalog(
            String schemaVersion,
            String knowledgeBaseId,
            String cleaningVersion,
            String chunkerVersion,
            List<ChunkCatalogEntry> chunks
    ) {
    }

    private record ChunkCatalogEntry(
            String chunkId,
            String documentId,
            String documentName,
            String documentType,
            String sourceHash,
            int chunkIndex,
            List<String> sectionPath,
            int startOffset,
            int endOffset,
            int contentChars,
            String content
    ) {

        private static ChunkCatalogEntry from(DocumentChunk chunk) {
            return new ChunkCatalogEntry(
                    chunk.chunkId(),
                    chunk.documentId(),
                    chunk.documentName(),
                    chunk.documentType().name(),
                    chunk.sourceHash(),
                    chunk.chunkIndex(),
                    chunk.sectionPath(),
                    chunk.startOffset(),
                    chunk.endOffset(),
                    chunk.content().length(),
                    chunk.content()
            );
        }
    }
}