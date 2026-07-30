package com.leo.careerforgeai.model.domain;

public record ModelMessage(
        ModelRole role,
        String content
) {
}