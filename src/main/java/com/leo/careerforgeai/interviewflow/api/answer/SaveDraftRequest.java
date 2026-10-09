package com.leo.careerforgeai.interviewflow.api.answer;

import com.leo.careerforgeai.interviewflow.application.answer.SaveDraftCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 接收服务端草稿CAS保存参数
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public record SaveDraftRequest(
        @NotNull
        @Size(max = SaveDraftCommand.MAX_CONTENT_CHARACTERS)
        String content,
        @NotNull @PositiveOrZero Long expectedVersion
) {

    SaveDraftCommand toCommand(UUID interviewId, UUID questionId) {
        return new SaveDraftCommand(
                interviewId,
                questionId,
                content,
                expectedVersion
        );
    }
}
