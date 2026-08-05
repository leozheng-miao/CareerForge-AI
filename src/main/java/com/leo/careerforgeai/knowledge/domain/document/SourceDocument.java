package com.leo.careerforgeai.knowledge.domain.document;

/**
 * @program: CareerForge-AI
 * @description: 保存一份已读取但尚未清洗的原始知识文档及其可追溯元数据
 * @author: Miao Zheng
 * @date: 2026-07-31 15:03
 **/
public record SourceDocument(
        String knowledgeBaseId,
        String documentId,
        String documentName,
        KnowledgeDocumentType documentType,
        String sourcePath,
        String sourceHash,
        String rawContent
) {

    public SourceDocument {
        requireText(knowledgeBaseId, "knowledgeBaseId");
        requireText(documentId, "documentId");
        requireText(documentName, "documentName");
        if (documentType == null) {
            throw new IllegalArgumentException("documentType 不能为空");
        }
        requireText(sourcePath, "sourcePath");
        if (sourceHash == null || !sourceHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceHash 必须是小写 SHA-256");
        }
        requireText(rawContent, "rawContent");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }
}