package com.leo.careerforgeai.model.domain.stream;

import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.exception.ModelErrorType;

public record ModelStreamEvent(
        ModelStreamEventType type,
        String requestId,
        String content,
        ModelUsage usage,
        ModelErrorType errorType
) {
}