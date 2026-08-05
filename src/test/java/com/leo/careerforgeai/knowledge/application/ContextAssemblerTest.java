package com.leo.careerforgeai.knowledge.application;

import com.leo.careerforgeai.knowledge.domain.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.context.AssembledContext;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextAssemblerTest {

    private final ContextAssembler assembler = new ContextAssembler();

    /** 验证同源重复被删除，但不同内容和不同来源仍被保留。 */
    @Test
    void shouldRemoveSameSourceDuplicatesAndKeepSourceDiversity() {
        RrfRankedChunk first = candidate('a', 1, "document-1", 0, 0, "Java 并发使用 CAS 和 AtomicInteger 保证共享计数器线程安全");
        RrfRankedChunk duplicate = candidate('b', 2, "document-1", 1, 5, "Java并发使用CAS和AtomicInteger保证共享计数器线程安全");
        RrfRankedChunk adjacentDistinct = candidate('c', 3, "document-1", 1, 100, "线程池需要根据 CPU 数量和 IO 等待时间配置核心线程数");
        RrfRankedChunk otherSource = candidate('d', 4, "document-2", 0, 0, "Java并发使用CAS和AtomicInteger保证共享计数器线程安全");

        AssembledContext result = assembler.assemble(List.of(first, duplicate, adjacentDistinct, otherSource), 10_000);

        assertThat(result.chunks()).extracting(DocumentChunk::chunkId).containsExactly(first.chunk().chunkId(), adjacentDistinct.chunk().chunkId(), otherSource.chunk().chunkId());
        assertThat(result.duplicateSkippedCount()).isEqualTo(1);
        assertThat(result.budgetSkippedCount()).isZero();
        assertThat(result.assemblerVersion()).isEqualTo(ContextAssembler.VERSION);
    }

    /** 验证超出预算的高排名 Chunk 被跳过后，较短候选仍可进入上下文。 */
    @Test
    void shouldSkipOversizedCandidateAndUseRemainingBudget() {
        RrfRankedChunk first = candidate('a', 1, "document-1", 0, 0, "A".repeat(60));
        RrfRankedChunk second = candidate('b', 2, "document-1", 1, 100, "B".repeat(60));
        RrfRankedChunk third = candidate('c', 3, "document-1", 2, 200, "C".repeat(20));

        AssembledContext result = assembler.assemble(List.of(first, second, third), 80);

        assertThat(result.chunks()).extracting(DocumentChunk::chunkId).containsExactly(first.chunk().chunkId(), third.chunk().chunkId());
        assertThat(result.usedContentChars()).isEqualTo(80);
        assertThat(result.budgetSkippedCount()).isEqualTo(1);
    }

    /** 验证空候选和非法预算边界。 */
    @Test
    void shouldSupportEmptyCandidatesAndRejectInvalidInput() {
        AssembledContext empty = assembler.assemble(List.of(), 1_000);

        assertThat(empty.chunks()).isEmpty();
        assertThat(empty.usedContentChars()).isZero();

        assertThatThrownBy(() -> assembler.assemble(null, 1_000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assembler.assemble(List.of(), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private RrfRankedChunk candidate(char idCharacter, int rank, String documentId, int chunkIndex, int startOffset, String content) {
        String sourceHash = documentId.equals("document-1") ? "1".repeat(64) : "2".repeat(64);
        DocumentChunk chunk = new DocumentChunk(
                "careerforge",
                documentId,
                "测试文档",
                KnowledgeDocumentType.INTERVIEW_EXPERIENCE,
                documentId + ".md",
                sourceHash,
                "markdown-cleaner-v2",
                "markdown-structure-v2|max=1000|overlap=120",
                String.valueOf(idCharacter).repeat(64),
                chunkIndex,
                List.of(),
                startOffset,
                startOffset + content.length(),
                content
        );
        return new RrfRankedChunk(chunk, rank, null, 1D / (60 + rank), rank);
    }
}