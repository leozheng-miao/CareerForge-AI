package com.leo.careerforgeai.model.domain;

public record ModelUsage(
        long inputTokens,
        long outputTokens,
        long totalTokens
) {
}