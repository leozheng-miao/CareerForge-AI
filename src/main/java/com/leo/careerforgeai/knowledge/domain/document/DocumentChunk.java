package com.leo.careerforgeai.knowledge.domain.document;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 保存可检索文本片段及其来源、章节、位置和预处理版本元数据
 * @author: Miao Zheng
 * @date: 2026-07-31
 **/
public record DocumentChunk(
        String knowledgeBaseId,
        String documentId,
        String documentName,
        KnowledgeDocumentType documentType,
        String sourcePath,
        String sourceHash,
        String cleaningVersion,
        String chunkerVersion,
        String chunkId,
        int chunkIndex,
        List<String> sectionPath,
        int startOffset,
        int endOffset,
        String content
) {

    public DocumentChunk {
        requireText(knowledgeBaseId, "knowledgeBaseId");
        requireText(documentId, "documentId");
        requireText(documentName, "documentName");
        if (documentType == null) throw new IllegalArgumentException("documentType 不能为空");
        requireText(sourcePath, "sourcePath");
        if (sourceHash == null || !sourceHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sourceHash 必须是小写 SHA-256");
        requireText(cleaningVersion, "cleaningVersion");
        requireText(chunkerVersion, "chunkerVersion");
        if (chunkId == null || !chunkId.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("chunkId 必须是小写 SHA-256");
        if (chunkIndex < 0) throw new IllegalArgumentException("chunkIndex 不能小于 0");
        if (sectionPath == null) throw new IllegalArgumentException("sectionPath 不能为空");
        sectionPath = List.copyOf(sectionPath);
        if (sectionPath.stream().anyMatch(value -> value == null || value.isBlank())) throw new IllegalArgumentException("sectionPath 不能包含空标题");
        if (startOffset < 0) throw new IllegalArgumentException("startOffset 不能小于 0");
        if (endOffset <= startOffset) throw new IllegalArgumentException("endOffset 必须大于 startOffset");
        requireText(content, "content");
    }

    /**
     * 生成供 BM25 和 Document Embedding 使用的文本，将章节路径补充到正文前
     * @return
     */
    public String retrievalText() {
        return sectionPath.isEmpty() ? content : String.join(" > ", sectionPath) + "\n\n" + content;
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + " 不能为空");
    }
}