package com.leo.careerforgeai.interviewflow.api.session;

import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewSession;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回快速面试会话的安全主状态
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public record InterviewSessionResponse(
        UUID interviewId,
        InterviewMode mode,
        SessionStage stage,
        long version
) {

    public static InterviewSessionResponse from(InterviewSession session) {
        Objects.requireNonNull(session, "session不能为null");
        return new InterviewSessionResponse(
                session.interviewId(),
                session.mode(),
                session.stage(),
                session.version()
        );
    }
}
