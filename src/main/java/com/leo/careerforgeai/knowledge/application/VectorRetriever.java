package com.leo.careerforgeai.knowledge.application;

import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 定义与 Elasticsearch 无关的向量 Top K 检索能力
 * @author: Miao Zheng
 * @date: 2026-08-04 15:02
 **/
public interface VectorRetriever {
    /** 使用查询向量在指定数据范围内执行 kNN 检索。 */
    RetrievalResult retrieve(List<Float> queryVector, RetrievalScope scope, int topK, int numCandidates);
}