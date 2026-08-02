package com.leo.careerforgeai.knowledge.infrastructure.document;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-31 17:35
 **/
class MarkdownBlockParserTest {

    private final MarkdownBlockParser parser = new MarkdownBlockParser();

    @Test
    void shouldParseParagraphsAndTopLevelListItems() {
        String content = "第一段第一行\n第一段第二行\n\n- 列表一\n  续行\n  - 嵌套项\n- 列表二\n\n1. 有序一\n2. 有序二\n\n最后一段";
        MarkdownSection section = section(content, 100);

        List<MarkdownBlock> blocks = parser.parse(section);

        assertThat(blocks).hasSize(6);
        assertBlock(blocks.get(0), MarkdownBlockType.PARAGRAPH, "第一段第一行\n第一段第二行");
        assertBlock(blocks.get(1), MarkdownBlockType.LIST_ITEM, "- 列表一\n  续行\n  - 嵌套项");
        assertBlock(blocks.get(2), MarkdownBlockType.LIST_ITEM, "- 列表二");
        assertBlock(blocks.get(3), MarkdownBlockType.LIST_ITEM, "1. 有序一");
        assertBlock(blocks.get(4), MarkdownBlockType.LIST_ITEM, "2. 有序二");
        assertBlock(blocks.get(5), MarkdownBlockType.PARAGRAPH, "最后一段");
        blocks.forEach(block -> assertThat(content.substring(block.startOffset() - 100, block.endOffset() - 100)).isEqualTo(block.content()));
    }

    @Test
    void shouldKeepFencedCodeAsOneBlockAndSeparateSurroundingParagraphs() {
        String content = "前文\n```java\nString first = \"A\";\n\nString second = \"B\";\n```\n后文";

        List<MarkdownBlock> blocks = parser.parse(section(content, 0));

        assertThat(blocks).hasSize(3);
        assertBlock(blocks.get(0), MarkdownBlockType.PARAGRAPH, "前文");
        assertBlock(blocks.get(1), MarkdownBlockType.FENCED_CODE, "```java\nString first = \"A\";\n\nString second = \"B\";\n```");
        assertBlock(blocks.get(2), MarkdownBlockType.PARAGRAPH, "后文");
    }

    @Test
    void shouldNotTreatMarkerLikeTextAsListItems() {
        String content = "-不是列表\n1.不是列表\n---\n普通正文";

        List<MarkdownBlock> blocks = parser.parse(section(content, 0));

        assertThat(blocks).hasSize(1);
        assertBlock(blocks.getFirst(), MarkdownBlockType.PARAGRAPH, content);
    }

    @Test
    void shouldKeepUnclosedFenceAsSingleCodeBlockUntilSectionEnd() {
        String content = "```text\n第一行\n\n第二行";

        List<MarkdownBlock> blocks = parser.parse(section(content, 20));

        assertThat(blocks).hasSize(1);
        assertBlock(blocks.getFirst(), MarkdownBlockType.FENCED_CODE, content);
        assertThat(blocks.getFirst().startOffset()).isEqualTo(20);
        assertThat(blocks.getFirst().endOffset()).isEqualTo(20 + content.length());
    }

    @Test
    void shouldRejectNullSection() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(IllegalArgumentException.class).hasMessage("section 不能为空");
    }

    private MarkdownSection section(String content, int startOffset) {
        return new MarkdownSection(List.of("文档", "章节"), startOffset, startOffset + content.length(), content);
    }

    private void assertBlock(MarkdownBlock block, MarkdownBlockType expectedType, String expectedContent) {
        assertThat(block.type()).isEqualTo(expectedType);
        assertThat(block.content()).isEqualTo(expectedContent);
    }

    @Test
    void shouldSeparateEquallyIndentedItemsAndKeepDeeperItemsNested() {
        String content = "  - 列表一\n    - 嵌套项\n  - 列表二";

        List<MarkdownBlock> blocks = parser.parse(section(content, 0));

        assertThat(blocks).hasSize(2);
        assertBlock(blocks.get(0), MarkdownBlockType.LIST_ITEM, "  - 列表一\n    - 嵌套项");
        assertBlock(blocks.get(1), MarkdownBlockType.LIST_ITEM, "  - 列表二");
    }
}