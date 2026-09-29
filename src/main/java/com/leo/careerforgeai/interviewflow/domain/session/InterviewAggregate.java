package com.leo.careerforgeai.interviewflow.domain.session;

import com.leo.careerforgeai.interviewflow.domain.timer.InterviewTimer;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 聚合快速面试会话、有效计时和跨回合预算计数
 * @author: Miao Zheng
 * @date: 2026-09-30
 * @param session 会话身份、归属、阶段和版本
 * @param timer 有效作答计时器
 * @param submittedAnswerCount 已正式提交的答案数量
 * @param followUpCount 全场已选择的追问数量
 * @param consecutiveFollowUpCount 当前环节连续追问数量
 * @param createdAt 会话创建时间
 */
public record InterviewAggregate(
        InterviewSession session,
        InterviewTimer timer,
        int submittedAnswerCount,
        int followUpCount,
        int consecutiveFollowUpCount,
        Instant createdAt
) {

    public InterviewAggregate {
        Objects.requireNonNull(session, "session不能为空");
        Objects.requireNonNull(timer, "timer不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");
        requireNonNegative(submittedAnswerCount, "submittedAnswerCount");
        requireNonNegative(followUpCount, "followUpCount");
        requireNonNegative(
                consecutiveFollowUpCount,
                "consecutiveFollowUpCount"
        );
        if (consecutiveFollowUpCount > followUpCount) {
            throw new IllegalArgumentException(
                    "consecutiveFollowUpCount不能超过followUpCount"
            );
        }
    }

    public static InterviewAggregate createQuick(
            UUID interviewId,
            ActorId ownerId,
            Instant createdAt,
            QuickInterviewPolicy policy
    ) {
        Objects.requireNonNull(policy, "policy不能为空");
        return new InterviewAggregate(
                InterviewSession.createQuick(interviewId, ownerId),
                InterviewTimer.notStarted(policy.duration()),
                0,
                0,
                0,
                createdAt
        );
    }

    InterviewAggregate changeStage(
            SessionStage nextStage,
            InterviewTimer nextTimer
    ) {
        return copy(
                nextSession(nextStage),
                nextTimer,
                submittedAnswerCount,
                followUpCount,
                consecutiveFollowUpCount
        );
    }

    InterviewAggregate changeTimer(InterviewTimer nextTimer) {
        return copy(
                session.incrementVersion(),
                nextTimer,
                submittedAnswerCount,
                followUpCount,
                consecutiveFollowUpCount
        );
    }

    InterviewAggregate recordSubmittedAnswer(
            SessionStage nextStage,
            InterviewTimer nextTimer
    ) {
        return copy(
                nextSession(nextStage),
                nextTimer,
                Math.addExact(submittedAnswerCount, 1),
                followUpCount,
                consecutiveFollowUpCount
        );
    }

    InterviewAggregate recordFollowUp() {
        return copy(
                session.incrementVersion(),
                timer,
                submittedAnswerCount,
                Math.addExact(followUpCount, 1),
                Math.addExact(consecutiveFollowUpCount, 1)
        );
    }

    InterviewAggregate startNextTopic() {
        return copy(
                session.incrementVersion(),
                timer,
                submittedAnswerCount,
                followUpCount,
                0
        );
    }

    private InterviewSession nextSession(SessionStage nextStage) {
        Objects.requireNonNull(nextStage, "nextStage不能为空");
        if (session.stage() == nextStage) {
            return session.incrementVersion();
        }
        return session.moveTo(nextStage);
    }

    private InterviewAggregate copy(
            InterviewSession nextSession,
            InterviewTimer nextTimer,
            int nextSubmittedAnswerCount,
            int nextFollowUpCount,
            int nextConsecutiveFollowUpCount
    ) {
        return new InterviewAggregate(
                nextSession,
                nextTimer,
                nextSubmittedAnswerCount,
                nextFollowUpCount,
                nextConsecutiveFollowUpCount,
                createdAt
        );
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + "不能小于0");
        }
    }
}
