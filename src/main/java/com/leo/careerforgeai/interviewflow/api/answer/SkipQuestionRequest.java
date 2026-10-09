package com.leo.careerforgeai.interviewflow.api.answer;

import com.leo.careerforgeai.interviewflow.application.answer.SkipQuestionCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 接收幂等跳过和草稿版本参数
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public record SkipQuestionRequest(
        @NotNull UUID requestId,
        @NotNull @PositiveOrZero Long expectedVersion
) {

    SkipQuestionCommand toCommand(UUID interviewId, UUID questionId) {
        return new SkipQuestionCommand(
                interviewId,
                questionId,
                requestId,
                expectedVersion
        );
    }
}
