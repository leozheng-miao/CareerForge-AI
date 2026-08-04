package com.leo.careerforgeai.knowledge.application;

import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalResult;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;

/**
 * @program: CareerForge-AI
 * @description: 定义与 Elasticsearch 无关的 BM25 检索能力
 * @author: Miao Zheng
 * @date: 2026-08-03 22:20
 **/
public interface Bm25Retriever {
    /** 在指定数据范围内执行 BM25 Top K 检索。 */
    RetrievalResult retrieve(String query, RetrievalScope scope, int topK);
}