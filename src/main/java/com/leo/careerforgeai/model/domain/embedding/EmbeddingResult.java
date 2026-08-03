package com.leo.careerforgeai.model.domain.embedding;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 保存模型名称、向量维度、顺序对齐的批量向量和真实调用耗时
 * @author: Miao Zheng
 * @date: 2026-08-02 23:30
 **/
public record EmbeddingResult(
        String model,
        int dimensions,
        List<List<Float>> vectors,
        long durationMs
) {
    public EmbeddingResult {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model 不能为空");
        if (dimensions <= 0) throw new IllegalArgumentException("dimensions 必须大于 0");
        if (vectors == null || vectors.isEmpty()) throw new IllegalArgumentException("vectors 不能为空");
        if (durationMs < 0) throw new IllegalArgumentException("durationMs 不能小于 0");
        vectors = vectors.stream().map(vector -> validateVector(vector, dimensions)).toList();
    }

    private static List<Float> validateVector(List<Float> vector, int dimensions) {
        if (vector == null || vector.size() != dimensions) throw new IllegalArgumentException("向量维度必须等于 dimensions");
        if (vector.stream().anyMatch(value -> value == null || !Float.isFinite(value))) throw new IllegalArgumentException("向量不能包含 null、NaN 或 Infinity");
        return List.copyOf(vector);
    }
}