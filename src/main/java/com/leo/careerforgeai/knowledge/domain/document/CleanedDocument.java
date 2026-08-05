package com.leo.careerforgeai.knowledge.domain.document;

/**
 * @program: CareerForge-AI
 * @description: 保存清洗后的 Markdown 内容、清洗版本及其原始文档追溯信息
 * @author: Miao Zheng
 * @date: 2026-07-31
 **/
public record CleanedDocument(
        SourceDocument sourceDocument,
        String cleaningVersion,
        String cleanedContent
) {

    public CleanedDocument {
        if (sourceDocument == null) throw new IllegalArgumentException("sourceDocument 不能为空");
        if (cleaningVersion == null || cleaningVersion.isBlank()) throw new IllegalArgumentException("cleaningVersion 不能为空");
        if (cleanedContent == null || cleanedContent.isBlank()) throw new IllegalArgumentException("cleanedContent 不能为空");
    }
}