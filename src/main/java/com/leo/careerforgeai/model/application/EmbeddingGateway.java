package com.leo.careerforgeai.model.application;

import com.leo.careerforgeai.model.domain.embedding.EmbeddingRequest;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingResult;

/**
 * @program: CareerForge-AI
 * @description: 以供应商无关的方式批量生成顺序对齐的 Embedding 向量
 * @author: Miao Zheng
 * @date: 2026-08-02 23:31
 **/
public interface EmbeddingGateway {
    EmbeddingResult embed(EmbeddingRequest request);
}