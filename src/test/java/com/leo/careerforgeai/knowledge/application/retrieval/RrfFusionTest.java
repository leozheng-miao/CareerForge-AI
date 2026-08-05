package com.leo.careerforgeai.knowledge.application.retrieval;

import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievedChunk;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * @program: CareerForge-AI
 * @description: 验证 RRF 融合计算、稳定排序及异常输入边界
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
class RrfFusionTest {

    private final RrfFusion fusion = new RrfFusion();

    /** 验证两路候选按 Chunk ID 合并并正确计算最终排名。 */
    @Test
    void shouldFuseBothRoutesByChunkId() {
        DocumentChunk a = chunk('a', "候选 A");
        DocumentChunk b = chunk('b', "候选 B");
        DocumentChunk c = chunk('c', "候选 C");
        DocumentChunk d = chunk('d', "候选 D");

        List<RrfRankedChunk> result = fusion.fuse(result(a, b, c), result(c, b, d), 10);

        assertThat(result).extracting(item -> item.chunk().chunkId()).containsExactly(c.chunkId(), b.chunkId(), a.chunkId(), d.chunkId());
        assertThat(result).extracting(RrfRankedChunk::finalRank).containsExactly(1, 2, 3, 4);

        RrfRankedChunk first = result.getFirst();
        assertThat(first.bm25Rank()).isEqualTo(3);
        assertThat(first.vectorRank()).isEqualTo(1);
        assertThat(first.rrfScore()).isCloseTo(1D / 63 + 1D / 61, within(1E-12));

        RrfRankedChunk bm25Only = result.get(2);
        assertThat(bm25Only.bm25Rank()).isEqualTo(1);
        assertThat(bm25Only.vectorRank()).isNull();
        assertThat(bm25Only.rrfScore()).isCloseTo(1D / 61, within(1E-12));

        RrfRankedChunk vectorOnly = result.get(3);
        assertThat(vectorOnly.bm25Rank()).isNull();
        assertThat(vectorOnly.vectorRank()).isEqualTo(3);
    }

    /** 验证相同 RRF Score 使用 Chunk ID 稳定排序且不依赖原始检索分数。 */
    @Test
    void shouldUseStableChunkIdTieBreak() {
        DocumentChunk a = chunk('a', "候选 A");
        DocumentChunk b = chunk('b', "候选 B");

        RetrievalResult firstBm25 = new RetrievalResult(List.of(new RetrievedChunk(a, 999D, 1)), 0);
        RetrievalResult firstVector = new RetrievalResult(List.of(new RetrievedChunk(b, 0.01D, 1)), 0);
        RetrievalResult secondBm25 = new RetrievalResult(List.of(new RetrievedChunk(b, 1D, 1)), 0);
        RetrievalResult secondVector = new RetrievalResult(List.of(new RetrievedChunk(a, 100D, 1)), 0);

        List<String> firstOrder = fusion.fuse(firstBm25, firstVector, 10).stream().map(item -> item.chunk().chunkId()).toList();
        List<String> secondOrder = fusion.fuse(secondBm25, secondVector, 10).stream().map(item -> item.chunk().chunkId()).toList();

        assertThat(firstOrder).containsExactly(a.chunkId(), b.chunkId());
        assertThat(secondOrder).containsExactly(a.chunkId(), b.chunkId());
    }

    /** 验证 Top K 截断以及两路均为空时返回空结果。 */
    @Test
    void shouldLimitTopKAndSupportEmptyResults() {
        DocumentChunk a = chunk('a', "候选 A");
        DocumentChunk b = chunk('b', "候选 B");
        DocumentChunk c = chunk('c', "候选 C");
        RetrievalResult empty = new RetrievalResult(List.of(), 0);

        assertThat(fusion.fuse(result(a, b, c), empty, 2)).hasSize(2);
        assertThat(fusion.fuse(empty, empty, 5)).isEmpty();
    }

    /** 验证同一路重复 Chunk、跨路线数据不一致及非法 Top K 会被拒绝。 */
    @Test
    void shouldRejectInvalidFusionInput() {
        DocumentChunk a = chunk('a', "候选 A");
        DocumentChunk changedA = chunk('a', "被篡改的候选 A");
        RetrievalResult duplicateBm25 = new RetrievalResult(List.of(new RetrievedChunk(a, 10D, 1), new RetrievedChunk(a, 9D, 2)), 0);
        RetrievalResult empty = new RetrievalResult(List.of(), 0);

        assertThatThrownBy(() -> fusion.fuse(duplicateBm25, empty, 5)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("重复");
        assertThatThrownBy(() -> fusion.fuse(result(a), result(changedA), 5)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("数据不一致");
        assertThatThrownBy(() -> fusion.fuse(empty, empty, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private RetrievalResult result(DocumentChunk... chunks) {
        List<RetrievedChunk> retrievedChunks = IntStream.range(0, chunks.length).mapToObj(index -> new RetrievedChunk(chunks[index], 100D - index, index + 1)).toList();
        return new RetrievalResult(retrievedChunks, 0);
    }

    private DocumentChunk chunk(char idCharacter, String content) {
        return new DocumentChunk(
                "careerforge",
                "document-1",
                "测试文档",
                KnowledgeDocumentType.INTERVIEW_EXPERIENCE,
                "测试文档.md",
                "f".repeat(64),
                "markdown-cleaner-v2",
                "markdown-structure-v2|max=1000|overlap=120",
                String.valueOf(idCharacter).repeat(64),
                0,
                List.of("测试文档"),
                0,
                content.length(),
                content
        );
    }
}