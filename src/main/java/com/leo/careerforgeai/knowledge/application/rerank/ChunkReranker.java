package com.leo.careerforgeai.knowledge.application.rerank;

import com.leo.careerforgeai.knowledge.domain.rerank.ChunkRerankResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RrfRankedChunk;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 根据用户问题重新排列 RRF 候选，但不得增加、删除或修改候选 Chunk
 *
 * Retriever
 * 从整个 Elasticsearch 索引中快速找出少量候选
 * → 强调召回率和速度
 *
 * Reranker
 * 只查看 RRF 返回的少量候选，重新判断它们与 Query 的相关顺序
 * → 强调排序精度
 *
 * @author: Miao Zheng
 * @date: 2026-08-05
 **/
public interface ChunkReranker {

    /** 返回重新排序后的相同候选集合，以及本次模型调用的可观测数据。 */
    ChunkRerankResult rerank(String query, List<RrfRankedChunk> candidates);
}