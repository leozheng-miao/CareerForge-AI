package com.leo.careerforgeai.knowledge.infrastructure.elasticsearch.indexing;

import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 表示最终写入 Elasticsearch _source 的完整 Chunk 文档
 *           DocumentChunk + 对应向量 + Embedding 模型信息
 * @author: Miao Zheng
 * @date: 2026-08-03 13:46
 **/
public record KnowledgeChunkIndexDocument(
        String knowledgeBaseId,
        String documentId,
        String documentName,
        String documentType,
        String sourcePath,
        String sourceHash,
        String cleaningVersion,
        String chunkerVersion,
        String embeddingModel,
        int embeddingDimensions,
        String chunkId,
        int chunkIndex,
        List<String> sectionPath,
        int startOffset,
        int endOffset,
        String content,
        String retrievalText,
        List<Float> embedding
) {
    public KnowledgeChunkIndexDocument {
        if (embeddingModel == null || embeddingModel.isBlank()) throw new IllegalArgumentException("embeddingModel 不能为空");
        if (embeddingDimensions <= 0) throw new IllegalArgumentException("embeddingDimensions 必须大于 0");
        if (sectionPath == null) throw new IllegalArgumentException("sectionPath 不能为空");
        sectionPath = List.copyOf(sectionPath);
        if (embedding == null || embedding.size() != embeddingDimensions) throw new IllegalArgumentException("embedding 维度必须等于 embeddingDimensions");
        if (embedding.stream().anyMatch(value -> value == null || !Float.isFinite(value))) throw new IllegalArgumentException("embedding 不能包含 null、NaN 或 Infinity");
        embedding = List.copyOf(embedding);
    }

    /** 将一个业务 Chunk 与其顺序对应的向量组合成 Elasticsearch 入库文档。 */
    public static KnowledgeChunkIndexDocument from(DocumentChunk chunk, String embeddingModel, int embeddingDimensions, List<Float> embedding) {
        if (chunk == null) throw new IllegalArgumentException("chunk 不能为空");
        return new KnowledgeChunkIndexDocument(
                chunk.knowledgeBaseId(),
                chunk.documentId(),
                chunk.documentName(),
                chunk.documentType().name(),
                chunk.sourcePath(),
                chunk.sourceHash(),
                chunk.cleaningVersion(),
                chunk.chunkerVersion(),
                embeddingModel,
                embeddingDimensions,
                chunk.chunkId(),
                chunk.chunkIndex(),
                chunk.sectionPath(),
                chunk.startOffset(),
                chunk.endOffset(),
                chunk.content(),
                chunk.retrievalText(),
                embedding
        );
    }
}