package com.leo.careerforgeai.model.infrastructure.ollama;

import com.leo.careerforgeai.model.domain.embedding.EmbeddingRequest;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 按照 Qwen3 Embedding 规则为 Query 添加固定版本指令，同时保持 Document 输入不变
 * 0.6b官方规则：https://huggingface.co/Qwen/Qwen3-Embedding-0.6B
 * @author: Miao Zheng
 * @date: 2026-08-02 23:41
 **/
public class Qwen3EmbeddingInputFormatter {
    static final String QUERY_INSTRUCTION_VERSION = "qwen3-career-retrieval-v1";
    static final String QUERY_INSTRUCTION = "Given a career-related query, retrieve relevant passages from job descriptions and interview experience documents that answer the query";

    List<String> format(EmbeddingRequest request) {
        if (request == null) throw new IllegalArgumentException("request 不能为空");

        return switch (request.purpose()) {
            case DOCUMENT -> request.inputs();
            case QUERY -> request.inputs().stream().map(this::formatQuery).toList();
        };
    }

    private String formatQuery(String query) {
        return "Instruct: " + QUERY_INSTRUCTION + "\nQuery:" + query;
    }
}