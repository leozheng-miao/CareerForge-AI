package com.leo.careerforgeai.model.domain.toolcalling;

/** 统一表示 Tool Calling 请求历史中的合法消息类型。 */
public sealed interface ToolCallingMessage permits ToolCallingTextMessage, AssistantToolCallsMessage, ToolResultMessage {
}