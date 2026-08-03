package com.leo.careerforgeai.model.domain.embedding;

/**
 * @program: CareerForge-AI
 * @description: 区分文档向量化和查询向量化，防止 Query 指令错误添加到文档正文
 * @author: Miao Zheng
 * @date: 2026-08-02 23:15
 **/
public enum EmbeddingPurpose {
    DOCUMENT,
    QUERY
}