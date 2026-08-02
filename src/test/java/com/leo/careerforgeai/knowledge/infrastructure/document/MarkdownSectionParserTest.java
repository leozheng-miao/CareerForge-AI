package com.leo.careerforgeai.knowledge.infrastructure.document;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-31 17:29
 **/
class MarkdownSectionParserTest {

    private final MarkdownSectionParser parser = new MarkdownSectionParser();

    @Test
    void shouldParsePreambleAndHierarchicalSectionsWithExactOffsets() {
        String markdown = "前言\n\n# 总标题\n\n总览\n\n## 子标题\n\n子内容\n\n### 细节\n\n细节内容";

        List<MarkdownSection> sections = parser.parse(markdown);

        assertThat(sections).hasSize(4);
        assertSection(sections.get(0), List.of(), "前言");
        assertSection(sections.get(1), List.of("总标题"), "总览");
        assertSection(sections.get(2), List.of("总标题", "子标题"), "子内容");
        assertSection(sections.get(3), List.of("总标题", "子标题", "细节"), "细节内容");
        sections.forEach(section -> assertThat(markdown.substring(section.startOffset(), section.endOffset())).isEqualTo(section.content()));
    }

    @Test
    void shouldIgnoreHeadingSyntaxInsideCodeFence() {
        String markdown = "# 文档\n\n```markdown\n## 不是标题\n\n代码内容\n```\n\n## 真标题\n\n正文";

        List<MarkdownSection> sections = parser.parse(markdown);

        assertThat(sections).hasSize(2);
        assertSection(sections.get(0), List.of("文档"), "```markdown\n## 不是标题\n\n代码内容\n```");
        assertSection(sections.get(1), List.of("文档", "真标题"), "正文");
    }

    @Test
    void shouldHandleContinuousAndEmptyHeadingsWithoutCreatingEmptySections() {
        String markdown = "# 根标题\n## \n### 子标题\n内容\n## 下一个\n### \n正文";

        List<MarkdownSection> sections = parser.parse(markdown);

        assertThat(sections).hasSize(2);
        assertSection(sections.get(0), List.of("根标题", "子标题"), "内容");
        assertSection(sections.get(1), List.of("根标题", "下一个"), "正文");
    }

    @Test
    void shouldRequireValidAtxHeadingSyntaxAndAtMostThreeLeadingSpaces() {
        String markdown = "#不是标题\n    ## 也不是标题\n   ## 有效标题\n正文";

        List<MarkdownSection> sections = parser.parse(markdown);

        assertThat(sections).hasSize(2);
        assertSection(sections.get(0), List.of(), "#不是标题\n    ## 也不是标题");
        assertSection(sections.get(1), List.of("有效标题"), "正文");
    }

    @Test
    void shouldRejectBlankMarkdown() {
        assertThatThrownBy(() -> parser.parse(" \n\t")).isInstanceOf(IllegalArgumentException.class).hasMessage("markdown 不能为空");
    }

    @Test
    void shouldParseChineseNumberedQuestionsAsLevelThreeSemanticHeadings() {
        String markdown = "# 面经汇总\n## 荔枝科技\n三、Prompt 模板引擎怎么设计？\n变量替换是基础。\n四、React Fiber 有什么帮助？\n渲染任务可以中断。";

        List<MarkdownSection> sections = parser.parse(markdown);

        assertThat(sections).hasSize(2);
        assertSection(
                sections.get(0),
                List.of("面经汇总", "荔枝科技", "三、Prompt 模板引擎怎么设计？"),
                "变量替换是基础。"
        );
        assertSection(
                sections.get(1),
                List.of("面经汇总", "荔枝科技", "四、React Fiber 有什么帮助？"),
                "渲染任务可以中断。"
        );
    }

    @Test
    void shouldIgnoreChineseNumberedHeadingInsideCodeFence() {
        String markdown = "# 文档\n```text\n一、这不是标题\n```\n正文";

        List<MarkdownSection> sections = parser.parse(markdown);

        assertThat(sections).hasSize(1);
        assertSection(sections.getFirst(), List.of("文档"), "```text\n一、这不是标题\n```\n正文");
    }

    private void assertSection(MarkdownSection section, List<String> expectedPath, String expectedContent) {
        assertThat(section.sectionPath()).isEqualTo(expectedPath);
        assertThat(section.content()).isEqualTo(expectedContent);
    }
}