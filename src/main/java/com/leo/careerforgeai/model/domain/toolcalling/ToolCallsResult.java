package com.leo.careerforgeai.model.domain.toolcalling;

import com.leo.careerforgeai.model.domain.ModelUsage;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 表示模型在当前轮请求应用按顺序处理一个或多个工具调用。 */
public record ToolCallsResult(
        String requestId,
        String model,
        List<ToolCall> toolCalls,
        ModelUsage usage
) implements ToolCallingModelResult {

    public ToolCallsResult {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId 不能为空");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model 不能为空");
        if (toolCalls == null || toolCalls.isEmpty()) throw new IllegalArgumentException("toolCalls 不能为空");
        if (toolCalls.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("toolCalls 不能包含 null");
        Objects.requireNonNull(usage, "usage 不能为空");

        toolCalls = List.copyOf(toolCalls);
        Set<String> ids = new HashSet<>();
        for (ToolCall toolCall : toolCalls) {
            if (!ids.add(toolCall.id())) throw new IllegalArgumentException("同一轮存在重复 Tool Call ID=" + toolCall.id());
        }
    }
}