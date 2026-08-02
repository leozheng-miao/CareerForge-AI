package com.leo.careerforgeai.knowledge.infrastructure.document;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-31 21:23
 **/
class MarkdownBlockChunkAssemblerTest {

    private final MarkdownBlockParser blockParser = new MarkdownBlockParser();
    private final MarkdownBlockChunkAssembler assembler = new MarkdownBlockChunkAssembler();

    @Test
    void shouldPackAdjacentBlocksWithoutExceedingMaximumLength() {
        String content = "第一段\n\n第二段\n\n第三段内容";
        MarkdownSection section = section(content, 100);

        List<ChunkDraft> drafts = assembler.assemble(section, blockParser.parse(section), 8);

        assertThat(drafts).hasSize(2);
        assertDraft(drafts.get(0), 100, 108, "第一段\n\n第二段");
        assertDraft(drafts.get(1), 110, 115, "第三段内容");
    }

    @Test
    void shouldPreserveOriginalSeparatorBetweenConsecutiveListItems() {
        String content = "- 一\n- 二\n- 三";
        MarkdownSection section = section(content, 0);

        List<ChunkDraft> drafts = assembler.assemble(section, blockParser.parse(section), 7);

        assertThat(drafts).hasSize(2);
        assertDraft(drafts.get(0), 0, 7, "- 一\n- 二");
        assertDraft(drafts.get(1), 8, 11, "- 三");
    }

    @Test
    void shouldAcceptChunkExactlyAtMaximumLength() {
        String content = "1234\n\n5678";
        MarkdownSection section = section(content, 20);

        List<ChunkDraft> drafts = assembler.assemble(section, blockParser.parse(section), content.length());

        assertThat(drafts).hasSize(1);
        assertDraft(drafts.getFirst(), 20, 20 + content.length(), content);
    }

    @Test
    void shouldRejectOversizedBlockUntilItHasBeenSplit() {
        String content = "123456";
        MarkdownSection section = section(content, 0);

        assertThatThrownBy(() -> assembler.assemble(section, blockParser.parse(section), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须先执行超长 Block 切分");
    }

    @Test
    void shouldRejectInvalidArguments() {
        MarkdownSection section = section("正文", 0);

        assertThatThrownBy(() -> assembler.assemble(null, List.of(), 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assembler.assemble(section, null, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assembler.assemble(section, List.of(), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assembler.assemble(section, List.of(), 100, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assembler.assemble(section, List.of(), 100, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    private MarkdownSection section(String content, int startOffset) {
        return new MarkdownSection(List.of("文档", "章节"), startOffset, startOffset + content.length(), content);
    }

    private void assertDraft(ChunkDraft draft, int expectedStart, int expectedEnd, String expectedContent) {
        assertThat(draft.startOffset()).isEqualTo(expectedStart);
        assertThat(draft.endOffset()).isEqualTo(expectedEnd);
        assertThat(draft.content()).isEqualTo(expectedContent);
    }

    @Test
    void shouldRepeatWholeTrailingBlocksWithinOverlapBudget() {
        String content = "- 一\n- 二\n- 三\n- 四";
        MarkdownSection section = section(content, 0);

        List<ChunkDraft> drafts = assembler.assemble(section, blockParser.parse(section), 7, 3);

        assertThat(drafts).hasSize(3);
        assertDraft(drafts.get(0), 0, 7, "- 一\n- 二");
        assertDraft(drafts.get(1), 4, 11, "- 二\n- 三");
        assertDraft(drafts.get(2), 8, 15, "- 三\n- 四");
    }

    @Test
    void shouldSkipOverlapWhenWholeBlockExceedsOverlapBudget() {
        String content = "12345\n\n67890";
        MarkdownSection section = section(content, 0);

        List<ChunkDraft> drafts = assembler.assemble(section, blockParser.parse(section), 5, 2);

        assertThat(drafts).hasSize(2);
        assertDraft(drafts.get(0), 0, 5, "12345");
        assertDraft(drafts.get(1), 7, 12, "67890");
    }
}