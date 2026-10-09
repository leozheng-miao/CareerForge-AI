package com.leo.careerforgeai.interviewflow.application.answer;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 承载带幂等ID的当前题跳过请求
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public record SkipQuestionCommand(
        UUID interviewId,
        UUID questionId,
        UUID requestId,
        long expectedVersion
) {

    public SkipQuestionCommand {
        Objects.requireNonNull(interviewId, "interviewId不能为null");
        Objects.requireNonNull(questionId, "questionId不能为null");
        Objects.requireNonNull(requestId, "requestId不能为null");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "expectedVersion不能小于0"
            );
        }
    }
}
