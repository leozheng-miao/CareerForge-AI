package com.leo.careerforgeai.knowledge.infrastructure.document.chunking;

import com.leo.careerforgeai.knowledge.domain.document.CleanedDocument;
import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.document.SourceDocument;
import com.leo.careerforgeai.knowledge.config.ChunkingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-02 02:07
 **/
class MarkdownChunkerTest {

    @Test
    void shouldCreateOrderedTraceableChunksFromHierarchicalMarkdown() {
        String markdown = "# 总标题\n\n## 第一节\n\n- 问题一\n- 问题二\n\n## 第二节\n\n第二节正文";
        CleanedDocument document = document(markdown);
        MarkdownChunker chunker = chunker(100, 10);

        List<DocumentChunk> chunks = chunker.chunk(document);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).sectionPath()).containsExactly("总标题", "第一节");
        assertThat(chunks.get(0).content()).isEqualTo("- 问题一\n- 问题二");
        assertThat(chunks.get(0).retrievalText()).isEqualTo("总标题 > 第一节\n\n- 问题一\n- 问题二");
        assertThat(chunks.get(1).sectionPath()).containsExactly("总标题", "第二节");
        assertThat(chunks.get(1).content()).isEqualTo("第二节正文");

        assertThat(chunks).extracting(DocumentChunk::chunkIndex).containsExactly(0, 1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.knowledgeBaseId()).isEqualTo("knowledge-base-1");
            assertThat(chunk.documentId()).isEqualTo("document-1");
            assertThat(chunk.sourceHash()).isEqualTo("0".repeat(64));
            assertThat(chunk.cleaningVersion()).isEqualTo("markdown-cleaner-v1");
            assertThat(chunk.chunkerVersion()).isEqualTo("markdown-structure-v2|max=100|overlap=10");
            assertThat(chunk.chunkId()).matches("[0-9a-f]{64}");
            assertThat(chunk.content().length()).isLessThanOrEqualTo(100);
            assertThat(markdown.substring(chunk.startOffset(), chunk.endOffset())).isEqualTo(chunk.content());
        });

        assertThat(chunker.chunk(document)).extracting(DocumentChunk::chunkId)
                .containsExactlyElementsOf(chunks.stream().map(DocumentChunk::chunkId).toList());
    }

    @Test
    void shouldApplyWholeBlockOverlapBetweenAdjacentChunks() {
        String markdown = "# 列表\n\n- 一\n- 二\n- 三\n- 四";

        List<DocumentChunk> chunks = chunker(7, 3).chunk(document(markdown));

        assertThat(chunks).extracting(DocumentChunk::content).containsExactly(
                "- 一\n- 二",
                "- 二\n- 三",
                "- 三\n- 四"
        );
    }

    @Test
    void shouldSplitOversizedParagraphAndThenAssembleWithOverlap() {
        String markdown = "# 标题\n\n甲句。乙句。丙句。丁句。";

        List<DocumentChunk> chunks = chunker(7, 3).chunk(document(markdown));

        assertThat(chunks).extracting(DocumentChunk::content).containsExactly(
                "甲句。乙句。",
                "乙句。丙句。",
                "丙句。丁句。"
        );
    }

    @Test
    void shouldChangeChunkIdWhenChunkingStrategyChanges() {
        CleanedDocument document = document("# 标题\n\n正文");

        DocumentChunk first = chunker(100, 10).chunk(document).getFirst();
        DocumentChunk second = chunker(80, 5).chunk(document).getFirst();

        assertThat(second.content()).isEqualTo(first.content());
        assertThat(second.chunkerVersion()).isNotEqualTo(first.chunkerVersion());
        assertThat(second.chunkId()).isNotEqualTo(first.chunkId());
    }

    @Test
    void shouldRejectDocumentContainingOnlyHeadings() {
        CleanedDocument document = document("# 标题\n\n## 子标题");

        assertThatThrownBy(() -> chunker(100, 10).chunk(document))
                .isInstanceOf(DocumentChunkingException.class)
                .hasMessageContaining("没有可生成 Chunk 的正文");
    }

    @Test
    void shouldPropagateOversizedFencedCodeFailure() {
        String markdown = "# 标题\n\n```text\n1234567890\n```";

        assertThatThrownBy(() -> chunker(10, 2).chunk(document(markdown)))
                .isInstanceOf(DocumentChunkingException.class)
                .hasMessageContaining("拒绝静默拆分代码");
    }

    @Test
    void shouldRejectNullDocument() {
        assertThatThrownBy(() -> chunker(100, 10).chunk(null)).isInstanceOf(IllegalArgumentException.class).hasMessage("document 不能为空");
    }

    @Test
    void shouldIncludeChineseNumberedQuestionInRetrievalText() {
        String markdown = "# 面经汇总\n## 荔枝科技\n三、Prompt 模板引擎怎么设计？\n变量替换是基础，函数调用负责扩展。";

        List<DocumentChunk> chunks = chunker(200, 20).chunk(document(markdown));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().sectionPath()).containsExactly(
                "面经汇总",
                "荔枝科技",
                "三、Prompt 模板引擎怎么设计？"
        );
        assertThat(chunks.getFirst().content()).isEqualTo("变量替换是基础，函数调用负责扩展。");
        assertThat(chunks.getFirst().retrievalText()).isEqualTo(
                "面经汇总 > 荔枝科技 > 三、Prompt 模板引擎怎么设计？\n\n变量替换是基础，函数调用负责扩展。"
        );
    }

    private MarkdownChunker chunker(int maxChunkChars, int overlapChars) {
        return new MarkdownChunker(new ChunkingProperties(maxChunkChars, overlapChars), new StableChunkIdGenerator());
    }

    private CleanedDocument document(String content) {
        SourceDocument source = new SourceDocument(
                "knowledge-base-1",
                "document-1",
                "测试材料",
                KnowledgeDocumentType.JOB_DESCRIPTION,
                "materials.md",
                "0".repeat(64),
                content
        );
        return new CleanedDocument(source, "markdown-cleaner-v1", content);
    }
}