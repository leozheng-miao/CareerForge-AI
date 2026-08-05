package com.leo.careerforgeai.knowledge.domain.answer;

import com.leo.careerforgeai.knowledge.domain.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.KnowledgeDocumentType;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 保存经过 Java 映射确认的真实 Chunk 来源和原文位置
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
public record RagCitation(
        String chunkId,
        String documentId,
        String documentName,
        KnowledgeDocumentType documentType,
        String sourcePath,
        String sourceHash,
        int chunkIndex,
        List<String> sectionPath,
        int startOffset,
        int endOffset
) {

    public RagCitation {
        requireText(chunkId, "chunkId");
        requireText(documentId, "documentId");
        requireText(documentName, "documentName");
        if (documentType == null) throw new IllegalArgumentException("documentType 不能为空");
        requireText(sourcePath, "sourcePath");
        if (sourceHash == null || !sourceHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sourceHash 必须是小写 SHA-256");
        if (chunkIndex < 0) throw new IllegalArgumentException("chunkIndex 不能小于 0");
        if (sectionPath == null) throw new IllegalArgumentException("sectionPath 不能为空");
        sectionPath = List.copyOf(sectionPath);
        if (sectionPath.stream().anyMatch(value -> value == null || value.isBlank())) throw new IllegalArgumentException("sectionPath 不能包含空标题");
        if (startOffset < 0) throw new IllegalArgumentException("startOffset 不能小于 0");
        if (endOffset <= startOffset) throw new IllegalArgumentException("endOffset 必须大于 startOffset");
    }

    /** 将本次检索上下文中的真实 DocumentChunk 映射为业务引用。 */
    public static RagCitation from(DocumentChunk chunk) {
        if (chunk == null) throw new IllegalArgumentException("chunk 不能为空");
        return new RagCitation(
                chunk.chunkId(),
                chunk.documentId(),
                chunk.documentName(),
                chunk.documentType(),
                chunk.sourcePath(),
                chunk.sourceHash(),
                chunk.chunkIndex(),
                chunk.sectionPath(),
                chunk.startOffset(),
                chunk.endOffset()
        );
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + " 不能为空");
    }
}