package com.leo.careerforgeai.model.domain;

public record ModelResponse(
        String requestId,
        String model,
        String content,
        ModelUsage usage
) {
}