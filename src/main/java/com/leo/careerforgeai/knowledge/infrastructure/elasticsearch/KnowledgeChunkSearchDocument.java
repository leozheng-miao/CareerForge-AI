package com.leo.careerforgeai.knowledge.infrastructure.elasticsearch;

import com.leo.careerforgeai.knowledge.domain.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.KnowledgeDocumentType;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 承接 Elasticsearch _source 中构造 DocumentChunk 所需的字段，不读取向量
 * @author: Miao Zheng
 * @date: 2026-08-03 22:22
 **/
public record KnowledgeChunkSearchDocument(
        String knowledgeBaseId,
        String documentId,
        String documentName,
        String documentType,
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

    /** 将 Elasticsearch `_source` 转回统一的领域 Chunk。 */
    public DocumentChunk toDomain() {
        return new DocumentChunk(knowledgeBaseId, documentId, documentName, KnowledgeDocumentType.valueOf(documentType), sourcePath, sourceHash, cleaningVersion, chunkerVersion, chunkId, chunkIndex, sectionPath, startOffset, endOffset, content);
    }
}