package com.leo.careerforgeai.knowledge.infrastructure.document;

import com.leo.careerforgeai.knowledge.domain.CleanedDocument;
import com.leo.careerforgeai.knowledge.domain.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.SourceDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * @program: CareerForge-AI
 * @description: 验证清洗规则的确定性、Markdown 结构保护、代码块边界和清洗失败语义。
 * @author: Miao Zheng
 * @date: 2026-07-31 16:39
 **/
class DocumentCleanerTest {

    private final DocumentCleaner cleaner = new DocumentCleaner();

    @Test
    void shouldNormalizeBomLineEndingsAndRepeatedBlankLines() {
        SourceDocument sourceDocument = sourceDocument("\uFEFF# 标题\r\n\r\n\r\n第一段\r第二段\r\n\r\n");

        CleanedDocument cleanedDocument = cleaner.clean(sourceDocument);

        assertThat(cleanedDocument.sourceDocument()).isSameAs(sourceDocument);
        assertThat(cleanedDocument.cleaningVersion()).isEqualTo(DocumentCleaner.CLEANING_VERSION);
        assertThat(cleanedDocument.cleanedContent()).isEqualTo("# 标题\n\n第一段\n第二段");
    }

    @Test
    void shouldPreserveMarkdownStructuresAndHardLineBreak() {
        String markdown = "# 标题\n\n- 列表一\n- 列表二\n\n> 引用\n\n[参考链接](https://example.com)\n\n第一行  \n第二行";

        CleanedDocument cleanedDocument = cleaner.clean(sourceDocument(markdown));

        assertThat(cleanedDocument.cleanedContent()).isEqualTo(markdown);
    }

    @Test
    void shouldCollapseBlankLinesOutsideButPreserveThemInsideBacktickFence() {
        String markdown = "# 示例\n\n\n```java\nString first = \"A\";\n\n\nString second = \"B\";\n```\n\n\n正文";
        String expected = "# 示例\n\n```java\nString first = \"A\";\n\n\nString second = \"B\";\n```\n\n正文";

        CleanedDocument cleanedDocument = cleaner.clean(sourceDocument(markdown));

        assertThat(cleanedDocument.cleanedContent()).isEqualTo(expected);
    }

    @Test
    void shouldPreserveBlankLinesInsideTildeFence() {
        String markdown = "~~~text\n第一行\n\n\n第二行\n~~~";

        CleanedDocument cleanedDocument = cleaner.clean(sourceDocument(markdown));

        assertThat(cleanedDocument.cleanedContent()).isEqualTo(markdown);
    }

    @Test
    void shouldRequireCompatibleClosingFenceLength() {
        String markdown = "````markdown\n第一行\n```\n\n\n第二行\n````\n\n\n正文";
        String expected = "````markdown\n第一行\n```\n\n\n第二行\n````\n\n正文";

        CleanedDocument cleanedDocument = cleaner.clean(sourceDocument(markdown));

        assertThat(cleanedDocument.cleanedContent()).isEqualTo(expected);
    }

    @Test
    void shouldRejectDocumentThatBecomesEmptyAfterCleaning() {
        SourceDocument sourceDocument = sourceDocument("\uFEFF \r\n\t");

        assertThatThrownBy(() -> cleaner.clean(sourceDocument))
                .isInstanceOfSatisfying(DocumentCleanException.class,
                        exception -> assertThat(exception.getErrorType()).isEqualTo(DocumentCleanErrorType.EMPTY_AFTER_CLEANING));
    }

    @Test
    void shouldRejectNullSourceDocument() {
        assertThatThrownBy(() -> cleaner.clean(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceDocument 不能为空");
    }

    @Test
    void shouldRemoveSourceMetadataOutsideFenceButPreserveItInsideFence() {
        String markdown = "# 标题\n\n> 来源：AI应用开发/面经/美团\n\n正文\n\n```text\n> 来源：代码示例\n```";
        String expected = "# 标题\n\n正文\n\n```text\n> 来源：代码示例\n```";

        CleanedDocument cleanedDocument = cleaner.clean(sourceDocument(markdown));

        assertThat(cleanedDocument.cleaningVersion()).isEqualTo("markdown-cleaner-v2");
        assertThat(cleanedDocument.cleanedContent()).isEqualTo(expected);
    }

    private SourceDocument sourceDocument(String rawContent) {
        return new SourceDocument(
                "knowledge-base-1",
                "document-1",
                "测试材料",
                KnowledgeDocumentType.JOB_DESCRIPTION,
                "materials.md",
                "0".repeat(64),
                rawContent
        );
    }
}