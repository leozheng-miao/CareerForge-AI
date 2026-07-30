package com.leo.careerforgeai.model.infrastructure.deepseek.dto;



import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-28 16:59
 **/
public record DeepSeekChatRequest(
        @JsonProperty("response_format")
        ResponseFormat responseFormat,
        String model,
        List<Message> messages,
        Thinking thinking,
        boolean stream,
        @JsonProperty("stream_options")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        StreamOptions streamOptions
        ) {
    public record Message (String role, String content) {}
    public record Thinking(String type) {}
    public record ResponseFormat(String type) {}
    public record StreamOptions( @JsonProperty("include_usage") boolean includeUsage) {}
}