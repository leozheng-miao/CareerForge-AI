package com.leo.careerforgeai.memory.application.extraction.dto.model;

import com.leo.careerforgeai.model.domain.ModelUsage;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 保存一次Memory提取的可信候选及真实模型调用元数据
 * @author: Miao Zheng
 * @date: 2026-08-13
 * @param candidates 通过Extractor全部校验的Memory候选
 * @param modelRequestId 最终成功模型调用的请求ID
 * @param modelUsage 本次提取全部模型调用的Token总用量
 * @param modelDurationMs 本次提取全部模型调用和输出校验的总耗时
 * @param modelCallCount 本次提取实际发生的模型调用次数
 **/
public record MemoryExtractionResult(
        List<ExtractedMemoryCandidate> candidates,
        String modelRequestId,
        ModelUsage modelUsage,
        long modelDurationMs,
        int modelCallCount
) {

    public MemoryExtractionResult {
        Objects.requireNonNull(candidates, "candidates不能为空");
        Objects.requireNonNull(modelUsage, "modelUsage不能为空");

        if (candidates.size() > 10 || candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("candidates数量或内容不合法");
        }
        candidates = List.copyOf(candidates);

        if (modelRequestId == null || modelRequestId.isBlank()) {
            throw new IllegalArgumentException("modelRequestId不能为空");
        }
        modelRequestId = modelRequestId.strip();

        if (modelUsage.inputTokens() < 0
                || modelUsage.outputTokens() < 0
                || modelUsage.totalTokens() < 0) {
            throw new IllegalArgumentException("modelUsage不能包含负数");
        }
        if (modelDurationMs < 0) {
            throw new IllegalArgumentException("modelDurationMs不能小于0");
        }
        if (modelCallCount <= 0) {
            throw new IllegalArgumentException("modelCallCount必须大于0");
        }
    }
}