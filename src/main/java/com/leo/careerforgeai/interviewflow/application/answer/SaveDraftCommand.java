package com.leo.careerforgeai.interviewflow.application.answer;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 承载当前题草稿的乐观锁保存请求
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public record SaveDraftCommand(
        UUID interviewId,
        UUID questionId,
        String content,
        long expectedVersion
) {

    public static final int MAX_CONTENT_CHARACTERS = 8000;

    public SaveDraftCommand {
        Objects.requireNonNull(interviewId, "interviewId不能为null");
        Objects.requireNonNull(questionId, "questionId不能为null");
        Objects.requireNonNull(content, "content不能为null");
        if (content.codePointCount(0, content.length())
                > MAX_CONTENT_CHARACTERS) {
            throw new IllegalArgumentException("回答长度不能超过8000字");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "expectedVersion不能小于0"
            );
        }
    }
}
