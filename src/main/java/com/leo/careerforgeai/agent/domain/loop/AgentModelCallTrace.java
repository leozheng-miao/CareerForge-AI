package com.leo.careerforgeai.agent.domain.loop;

import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.exception.ModelErrorType;

/**
 * @program: CareerForge-AI
 * @description: 记录单轮 Agent 模型调用的脱敏元数据、耗时和 Token。
 * @author: Miao Zheng
 * @date: 2026-08-06 17:04
 */
public record AgentModelCallTrace(
        int iteration,
        String modelRequestId,
        String model,
        AgentModelOutcome outcome,
        long durationMs,
        long estimatedInputTokens,
        ModelUsage usage,
        ModelErrorType errorType
) {

    public AgentModelCallTrace {
        if (iteration <= 0) throw new IllegalArgumentException("iteration 必须大于 0");
        if (outcome == null) throw new IllegalArgumentException("outcome 不能为空");
        if (durationMs < 0) throw new IllegalArgumentException("durationMs 不能小于 0");
        if (estimatedInputTokens < 0) throw new IllegalArgumentException("estimatedInputTokens 不能小于 0");

        if (outcome == AgentModelOutcome.FAILURE) {
            if (errorType == null) throw new IllegalArgumentException("失败模型调用必须包含 errorType");
        } else {
            if (modelRequestId == null || modelRequestId.isBlank()) {
                throw new IllegalArgumentException("成功模型调用必须包含 modelRequestId");
            }
            if (model == null || model.isBlank()) throw new IllegalArgumentException("成功模型调用必须包含 model");
            if (usage == null) throw new IllegalArgumentException("成功模型调用必须包含 usage");
            if (errorType != null) throw new IllegalArgumentException("成功模型调用不能包含 errorType");
        }

        validateUsage(usage);
    }

    private static void validateUsage(ModelUsage usage) {
        if (usage == null) return;
        if (usage.inputTokens() < 0 || usage.outputTokens() < 0 || usage.totalTokens() < 0) {
            throw new IllegalArgumentException("Token usage 不能包含负数");
        }
    }
}