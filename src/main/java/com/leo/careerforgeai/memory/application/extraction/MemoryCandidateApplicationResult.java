package com.leo.careerforgeai.memory.application.extraction;

import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.model.domain.ModelUsage;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 返回新执行或历史回放的Memory提取结果及原始模型审计信息
 * @author: Miao Zheng
 * @date: 2026-08-14
 * @param candidates 本次返回的Memory记录
 * @param modelRequestId 原始成功提取的模型请求ID
 * @param modelUsage 原始成功提取的Token总用量
 * @param modelDurationMs 原始成功提取的模型调用和校验耗时
 * @param modelCallCount 原始成功提取的模型调用次数
 * @param replayed 是否通过历史成功Receipt直接回放
 **/
public record MemoryCandidateApplicationResult(
        List<MemoryItem> candidates,
        String modelRequestId,
        ModelUsage modelUsage,
        long modelDurationMs,
        int modelCallCount,
        boolean replayed
) {

    public MemoryCandidateApplicationResult {
        Objects.requireNonNull(candidates, "candidates不能为空");
        Objects.requireNonNull(modelUsage, "modelUsage不能为空");

        if (candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("candidates不能包含空值");
        }
        if (modelRequestId == null || modelRequestId.isBlank()) {
            throw new IllegalArgumentException("modelRequestId不能为空");
        }
        if (modelDurationMs < 0) {
            throw new IllegalArgumentException("modelDurationMs不能小于0");
        }
        if (modelCallCount < 1 || modelCallCount > 2) {
            throw new IllegalArgumentException("modelCallCount必须在1到2之间");
        }

        candidates = List.copyOf(candidates);
        modelRequestId = modelRequestId.strip();
    }
}