package com.leo.careerforgeai.model.infrastructure.deepseek.toolcalling.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 表达 DeepSeek 非流式 Tool Calling 响应协议。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeepSeekToolCallingResponse(
        String id,
        String model,
        List<Choice> choices,
        Usage usage
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            int index,
            Message message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String role,
            String content,
            @JsonProperty("tool_calls") List<ToolCall> toolCalls
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolCall(
            String id,
            String type,
            FunctionCall function
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FunctionCall(
            String name,
            String arguments
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") long promptTokens,
            @JsonProperty("completion_tokens") long completionTokens,
            @JsonProperty("total_tokens") long totalTokens
    ) {
    }
}