package com.leo.careerforgeai.knowledge.application.indexing;

import com.leo.careerforgeai.knowledge.domain.document.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.indexing.KnowledgeIndexResult;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingResult;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 以 Elasticsearch 无关的方式定义批量写入 Chunk 和对应向量的能力
 * @author: Miao Zheng
 * @date: 2026-08-03 13:53
 **/
public interface KnowledgeIndex {
    /** 按列表位置将每个 Chunk 与对应向量批量写入知识索引。 */
    KnowledgeIndexResult index(List<DocumentChunk> chunks, EmbeddingResult embeddingResult);

    /** 将查询 Alias 原子切换到本次成功构建的索引版本。 */
    void activateCurrentVersion();
}