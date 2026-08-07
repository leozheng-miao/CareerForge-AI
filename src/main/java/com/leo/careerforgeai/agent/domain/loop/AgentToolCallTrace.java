package com.leo.careerforgeai.agent.domain.loop;

import com.leo.careerforgeai.agent.domain.tool.ToolExecutionErrorType;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
import com.leo.careerforgeai.agent.domain.tool.ToolImplementationType;
import com.leo.careerforgeai.model.domain.ModelUsage;

/**
 * @program: CareerForge-AI
 * @description: 记录单次工具调用的关联、实现类型、执行状态、大小、耗时和内部模型 Token。
 * @author: Miao Zheng
 * @date: 2026-08-06 17:04
 */
public record AgentToolCallTrace(
        int iteration,
        int sequence,
        String toolCallId,
        String toolName,
        ToolImplementationType implementationType,
        ToolExecutionStatus status,
        long durationMs,
        int inputSize,
        int outputSize,
        Integer resultCount,
        ToolExecutionErrorType errorType,
        ModelUsage modelUsage,
        Long modelDurationMs
) {

    public AgentToolCallTrace {
        if (iteration <= 0) throw new IllegalArgumentException("iteration 必须大于 0");
        if (sequence <= 0) throw new IllegalArgumentException("sequence 必须大于 0");
        if (toolCallId == null || toolCallId.isBlank()) throw new IllegalArgumentException("toolCallId 不能为空");
        if (toolName == null || toolName.isBlank()) throw new IllegalArgumentException("toolName 不能为空");
        if (status == null) throw new IllegalArgumentException("status 不能为空");
        if (implementationType == null
                && !(status == ToolExecutionStatus.FAILURE
                && errorType == ToolExecutionErrorType.UNKNOWN_TOOL)) {
            throw new IllegalArgumentException("只有未知工具失败允许缺少 implementationType");
        }
        if (durationMs < 0) throw new IllegalArgumentException("durationMs 不能小于 0");
        if (inputSize < 0 || outputSize < 0) throw new IllegalArgumentException("inputSize 和 outputSize 不能小于 0");
        if (resultCount != null && resultCount < 0) throw new IllegalArgumentException("resultCount 不能小于 0");
        if (modelDurationMs != null && modelDurationMs < 0) {
            throw new IllegalArgumentException("modelDurationMs 不能小于 0");
        }
        if (modelDurationMs != null && implementationType != ToolImplementationType.MODEL_BACKED) {
            throw new IllegalArgumentException("只有MODEL_BACKED工具能够记录独立模型耗时");
        }

        if (status == ToolExecutionStatus.SUCCESS && errorType != null) {
            throw new IllegalArgumentException("成功工具调用不能包含 errorType");
        }
        if (status == ToolExecutionStatus.FAILURE && errorType == null) {
            throw new IllegalArgumentException("失败工具调用必须包含 errorType");
        }
        if (modelUsage != null && (modelUsage.inputTokens() < 0
                || modelUsage.outputTokens() < 0
                || modelUsage.totalTokens() < 0)) {
            throw new IllegalArgumentException("工具内部模型 Token usage 不能包含负数");
        }
    }
}