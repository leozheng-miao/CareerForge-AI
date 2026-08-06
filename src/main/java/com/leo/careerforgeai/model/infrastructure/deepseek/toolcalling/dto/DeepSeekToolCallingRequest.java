package com.leo.careerforgeai.model.infrastructure.deepseek.toolcalling.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** 表达 DeepSeek 非流式 Tool Calling 请求协议。 */
public record DeepSeekToolCallingRequest(
        String model,
        List<Message> messages,
        List<Tool> tools,
        @JsonProperty("tool_choice") String toolChoice,
        Thinking thinking,
        @JsonProperty("max_tokens") int maxTokens,
        boolean stream
) {

    public record Message(
            String role,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String content,
            @JsonProperty("tool_calls")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            List<ToolCall> toolCalls,
            @JsonProperty("tool_call_id")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String toolCallId
    ) {
    }

    public record ToolCall(
            String id,
            String type,
            FunctionCall function
    ) {
    }

    public record FunctionCall(
            String name,
            String arguments
    ) {
    }

    public record Tool(
            String type,
            FunctionDefinition function
    ) {
    }

    public record FunctionDefinition(
            String name,
            String description,
            boolean strict,
            JsonNode parameters
    ) {
    }

    public record Thinking(String type) {
    }
}