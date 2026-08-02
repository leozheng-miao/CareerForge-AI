package com.leo.careerforgeai.knowledge.infrastructure.document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-31 22:30
 **/
class OversizedMarkdownBlockSplitterTest {

    private final OversizedMarkdownBlockSplitter splitter = new OversizedMarkdownBlockSplitter();
    private final MarkdownBlockChunkAssembler assembler = new MarkdownBlockChunkAssembler();

    @Test
    void shouldSplitOversizedParagraphAtSentenceBoundariesAndAllowBlockOverlap() {
        String content = "甲句。乙句。丙句。丁句。";
        MarkdownBlock source = block(MarkdownBlockType.PARAGRAPH, content, 0);

        List<MarkdownBlock> blocks = splitter.split(List.of(source), 7);
        MarkdownSection section = new MarkdownSection(List.of("文档", "章节"), 0, content.length(), content);
        List<ChunkDraft> drafts = assembler.assemble(section, blocks, 7, 3);

        assertThat(blocks).extracting(MarkdownBlock::content).containsExactly("甲句。", "乙句。", "丙句。", "丁句。");
        assertThat(drafts).extracting(ChunkDraft::content).containsExactly(
                "甲句。乙句。",
                "乙句。丙句。",
                "丙句。丁句。"
        );
    }

    @Test
    void shouldHardSplitLongTextWithoutSemanticBoundary() {
        String content = "ABCDEFGHIJK";
        MarkdownBlock source = block(MarkdownBlockType.PARAGRAPH, content, 10);

        List<MarkdownBlock> blocks = splitter.split(List.of(source), 5);

        assertThat(blocks).extracting(MarkdownBlock::content).containsExactly("ABCDE", "FGHIJ", "K");
        assertThat(blocks.get(0).startOffset()).isEqualTo(10);
        assertThat(blocks.get(0).endOffset()).isEqualTo(15);
        assertThat(blocks.get(2).startOffset()).isEqualTo(20);
        assertThat(blocks.get(2).endOffset()).isEqualTo(21);
    }

    @Test
    void shouldKeepBlockWhenItDoesNotExceedMaximumLength() {
        MarkdownBlock source = block(MarkdownBlockType.LIST_ITEM, "- 熟悉 Java", 0);

        List<MarkdownBlock> blocks = splitter.split(List.of(source), 100);

        assertThat(blocks).containsExactly(source);
    }

    @Test
    void shouldRejectOversizedFencedCodeInsteadOfBreakingItsStructure() {
        String content = "```text\n1234567890\n```";
        MarkdownBlock source = block(MarkdownBlockType.FENCED_CODE, content, 0);

        assertThatThrownBy(() -> splitter.split(List.of(source), 10))
                .isInstanceOf(DocumentChunkingException.class)
                .hasMessageContaining("拒绝静默拆分代码");
    }

    @Test
    void shouldRejectInvalidArguments() {
        assertThatThrownBy(() -> splitter.split(null, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> splitter.split(List.of(), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private MarkdownBlock block(MarkdownBlockType type, String content, int startOffset) {
        return new MarkdownBlock(type, startOffset, startOffset + content.length(), content);
    }
}