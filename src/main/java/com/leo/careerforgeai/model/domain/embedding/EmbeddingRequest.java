package com.leo.careerforgeai.model.domain.embedding;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 表示一批按原顺序提交、用途明确且不可变的 Embedding 文本
 * @author: Miao Zheng
 * @date: 2026-08-02 23:15
 **/
public record EmbeddingRequest(
        EmbeddingPurpose purpose,
        List<String> inputs
) {
    public EmbeddingRequest {
        if (purpose == null) throw new IllegalArgumentException("purpose 不能为空");
        if (inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("inputs 不能为空");
        if (inputs.stream().anyMatch(input -> input == null || input.isBlank())) throw new IllegalArgumentException("inputs 不能包含空文本");
        inputs = List.copyOf(inputs);
    }
}