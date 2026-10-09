package com.leo.careerforgeai.interviewflow.application.session;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示面试不存在或不属于当前用户
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public final class InterviewFlowNotFoundException extends RuntimeException {

    private final UUID interviewId;

    public InterviewFlowNotFoundException(UUID interviewId) {
        super("面试不存在或不属于当前用户");
        this.interviewId = Objects.requireNonNull(
                interviewId,
                "interviewId不能为null"
        );
    }

    public UUID interviewId() {
        return interviewId;
    }
}
