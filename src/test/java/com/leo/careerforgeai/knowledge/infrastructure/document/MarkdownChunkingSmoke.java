package com.leo.careerforgeai.knowledge.infrastructure.document;

import com.leo.careerforgeai.knowledge.domain.CleanedDocument;
import com.leo.careerforgeai.knowledge.domain.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.SourceDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Comparator;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 使用两份真实知识材料验证完整 Markdown Chunking 结果及其可追溯性
 * @author: Miao Zheng
 * @date: 2026-08-02
 **/
@SpringBootTest
class MarkdownChunkingSmoke {

    @Autowired
    private MarkdownDocumentLoader loader;

    @Autowired
    private DocumentCleaner cleaner;

    @Autowired
    private MarkdownChunker chunker;

    @Autowired
    private ChunkingProperties properties;

    @Test
    void shouldChunkCurrentKnowledgeCorpus() {
        List<SourceDocument> sourceDocuments = loader.loadAll();
        Set<String> allChunkIds = new HashSet<>();
        int totalChunks = 0;

        assertThat(sourceDocuments).hasSize(2);

        for (SourceDocument sourceDocument : sourceDocuments) {
            CleanedDocument cleanedDocument = cleaner.clean(sourceDocument);
            List<DocumentChunk> chunks = chunker.chunk(cleanedDocument);
            List<DocumentChunk> repeatedChunks = chunker.chunk(cleanedDocument);

            assertThat(chunks).isNotEmpty();
            assertThat(chunks).extracting(DocumentChunk::chunkIndex)
                    .containsExactlyElementsOf(IntStream.range(0, chunks.size()).boxed().toList());
            assertThat(chunks).extracting(DocumentChunk::chunkId).doesNotHaveDuplicates();
            assertThat(chunks).extracting(DocumentChunk::chunkId)
                    .containsExactlyElementsOf(repeatedChunks.stream().map(DocumentChunk::chunkId).toList());

            assertThat(chunks).allSatisfy(chunk -> {
                assertThat(chunk.documentId()).isEqualTo(sourceDocument.documentId());
                assertThat(chunk.sourceHash()).isEqualTo(sourceDocument.sourceHash());
                assertThat(chunk.chunkerVersion()).isEqualTo(properties.chunkerVersion());
                assertThat(chunk.sectionPath()).isNotEmpty();
                assertThat(chunk.content()).isNotBlank();
                assertThat(chunk.content().length()).isLessThanOrEqualTo(properties.getMaxChunkChars());
                assertThat(cleanedDocument.cleanedContent().substring(chunk.startOffset(), chunk.endOffset())).isEqualTo(chunk.content());
                assertThat(chunk.retrievalText()).endsWith(chunk.content());
                assertThat(allChunkIds.add(chunk.chunkId())).as("Chunk ID 必须在整个知识库中唯一").isTrue();
            });

            IntSummaryStatistics lengthStatistics = chunks.stream().mapToInt(chunk -> chunk.content().length()).summaryStatistics();
            long overlapPairs = countOverlapPairs(chunks);
            DocumentChunk sample = chunks.stream().max(Comparator.comparingInt(chunk -> chunk.content().length())).orElseThrow();

            System.out.printf(
                    "documentId=%s, chunks=%d, minChars=%d, maxChars=%d, avgChars=%.2f, overlapPairs=%d, chunkerVersion=%s%n",
                    sourceDocument.documentId(),
                    chunks.size(),
                    lengthStatistics.getMin(),
                    lengthStatistics.getMax(),
                    lengthStatistics.getAverage(),
                    overlapPairs,
                    properties.chunkerVersion()
            );
            System.out.printf(
                    "sampleChunkId=%s, sectionPath=%s, chars=%d, preview=%s%n",
                    sample.chunkId().substring(0, 12),
                    sample.sectionPath(),
                    sample.content().length(),
                    preview(sample.content())
            );

            totalChunks += chunks.size();
            DocumentChunk smallest = chunks.stream().min(Comparator.comparingInt(chunk -> chunk.content().length())).orElseThrow();
            System.out.printf(
                    "smallestChunkIndex=%d, sectionPath=%s, chars=%d, preview=%s%n",
                    smallest.chunkIndex(),
                    smallest.sectionPath(),
                    smallest.content().length(),
                    preview(smallest.content())
            );

            if (sourceDocument.documentId().equals("ai-interview-summary")) {
                chunks.stream()
                        .filter(chunk -> chunk.sectionPath().contains("荔枝科技"))
                        .forEach(chunk -> System.out.printf(
                                "auditChunkIndex=%d, sectionPath=%s, chars=%d, offsets=%d-%d, preview=%s%n",
                                chunk.chunkIndex(),
                                chunk.sectionPath(),
                                chunk.content().length(),
                                chunk.startOffset(),
                                chunk.endOffset(),
                                preview(chunk.content())
                        ));
            }
        }

        assertThat(allChunkIds).hasSize(totalChunks);
        System.out.printf("totalDocuments=%d, totalChunks=%d, uniqueChunkIds=%d%n", sourceDocuments.size(), totalChunks, allChunkIds.size());
    }

    private long countOverlapPairs(List<DocumentChunk> chunks) {
        return IntStream.range(1, chunks.size())
                .filter(index -> chunks.get(index).sectionPath().equals(chunks.get(index - 1).sectionPath()))
                .filter(index -> chunks.get(index).startOffset() < chunks.get(index - 1).endOffset())
                .count();
    }

    private String preview(String content) {
        String singleLine = content.replace('\n', ' ');
        return singleLine.length() <= 160 ? singleLine : singleLine.substring(0, 160) + "...";
    }
}