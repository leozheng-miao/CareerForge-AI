package com.leo.careerforgeai.interviewflow.application.answer;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 承载带幂等ID的正式答案冻结请求
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public record SubmitAnswerCommand(
        UUID interviewId,
        UUID questionId,
        UUID requestId,
        String content,
        long expectedVersion
) {

    public SubmitAnswerCommand {
        Objects.requireNonNull(interviewId, "interviewId不能为null");
        Objects.requireNonNull(questionId, "questionId不能为null");
        Objects.requireNonNull(requestId, "requestId不能为null");
        Objects.requireNonNull(content, "content不能为null");
        if (content.isBlank()) {
            throw new IllegalArgumentException(
                    "正式回答不能为空，请使用跳过操作"
            );
        }
        if (content.codePointCount(0, content.length())
                > SaveDraftCommand.MAX_CONTENT_CHARACTERS) {
            throw new IllegalArgumentException("回答长度不能超过8000字");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "expectedVersion不能小于0"
            );
        }
    }
}
