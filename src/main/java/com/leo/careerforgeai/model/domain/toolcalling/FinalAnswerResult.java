package com.leo.careerforgeai.model.domain.toolcalling;

import com.leo.careerforgeai.model.domain.ModelUsage;

import java.util.Objects;

/** 表示模型已结束工具调用过程并返回最终内容。 */
public record FinalAnswerResult(
        String requestId,
        String model,
        String content,
        ModelUsage usage
) implements ToolCallingModelResult {

    public FinalAnswerResult {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId 不能为空");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model 不能为空");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("最终回答不能为空");
        Objects.requireNonNull(usage, "usage 不能为空");
    }
}