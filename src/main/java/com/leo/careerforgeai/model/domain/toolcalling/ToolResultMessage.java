package com.leo.careerforgeai.model.domain.toolcalling;

/** 表示 Java 对某次 Tool Call 的结构化执行结果回传消息。 */
public record ToolResultMessage(
        String toolCallId,
        String toolName,
        String content
) implements ToolCallingMessage {

    public ToolResultMessage {
        if (toolCallId == null || toolCallId.isBlank()) throw new IllegalArgumentException("toolCallId 不能为空");
        if (toolName == null || toolName.isBlank()) throw new IllegalArgumentException("toolName 不能为空");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Tool Result 内容不能为空");
    }
}