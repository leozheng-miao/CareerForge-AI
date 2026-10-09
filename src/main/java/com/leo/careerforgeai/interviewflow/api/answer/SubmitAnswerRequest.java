package com.leo.careerforgeai.interviewflow.api.answer;

import com.leo.careerforgeai.interviewflow.application.answer.SaveDraftCommand;
import com.leo.careerforgeai.interviewflow.application.answer.SubmitAnswerCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 接收幂等正式提交与答案冻结参数
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public record SubmitAnswerRequest(
        @NotNull UUID requestId,
        @NotBlank
        @Size(max = SaveDraftCommand.MAX_CONTENT_CHARACTERS)
        String content,
        @NotNull @PositiveOrZero Long expectedVersion
) {

    SubmitAnswerCommand toCommand(UUID interviewId, UUID questionId) {
        return new SubmitAnswerCommand(
                interviewId,
                questionId,
                requestId,
                content,
                expectedVersion
        );
    }
}
