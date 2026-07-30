package com.leo.careerforgeai.model.infrastructure.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-29 16:35
 **/
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeepSeekStreamChunk(
        String id,
        String model,
        List<Choice> choices,
        DeepSeekChatResponse.Usage usage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            int index,
            Delta delta,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Delta(
            String role,
            String content
    ) {
    }
}