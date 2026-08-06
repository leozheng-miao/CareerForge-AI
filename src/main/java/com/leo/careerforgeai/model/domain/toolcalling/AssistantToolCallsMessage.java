package com.leo.careerforgeai.model.domain.toolcalling;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 表示需要在下一轮请求中原样回放的 Assistant Tool Calls。 */
public record AssistantToolCallsMessage(List<ToolCall> toolCalls) implements ToolCallingMessage {

    public AssistantToolCallsMessage {
        if (toolCalls == null || toolCalls.isEmpty()) throw new IllegalArgumentException("toolCalls 不能为空");
        if (toolCalls.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("toolCalls 不能包含 null");

        toolCalls = List.copyOf(toolCalls);
        Set<String> ids = new HashSet<>();
        for (ToolCall toolCall : toolCalls) {
            if (!ids.add(toolCall.id())) throw new IllegalArgumentException("Assistant 消息包含重复 Tool Call ID=" + toolCall.id());
        }
    }
}