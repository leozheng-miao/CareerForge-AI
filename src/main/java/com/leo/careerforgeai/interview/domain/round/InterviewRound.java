package com.leo.careerforgeai.interview.domain.round;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 保存单轮面试从问题就绪、回答完成到评审完成的状态和CAS版本
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param roundId 回合UUID
 * @param interviewId 所属面试UUID
 * @param ownerId 所属用户
 * @param roundNo 从1开始的回合号
 * @param status 当前回合状态
 * @param version 乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param answeredAt 回答提交时间
 * @param reviewedAt 评审完成时间
 **/
public record InterviewRound(
        UUID roundId,
        UUID interviewId,
        ActorId ownerId,
        int roundNo,
        InterviewRoundStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant answeredAt,
        Instant reviewedAt
) {

    public InterviewRound {
        Objects.requireNonNull(roundId, "roundId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt不能为空");

        if (roundNo < 1) throw new IllegalArgumentException("roundNo必须从1开始");
        if (version < 0) throw new IllegalArgumentException("version不能小于0");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt不能早于createdAt");
        validateLifecycle(status, createdAt, answeredAt, reviewedAt);
    }

    public static InterviewRound questionReady(
            UUID roundId,
            UUID interviewId,
            ActorId ownerId,
            int roundNo,
            Instant now
    ) {
        Objects.requireNonNull(now, "now不能为空");
        return new InterviewRound(
                roundId, interviewId, ownerId, roundNo,
                InterviewRoundStatus.QUESTION_READY, 0,
                now, now, null, null
        );
    }

    public InterviewRound answer(Instant now) {
        return transitionTo(InterviewRoundStatus.ANSWERED, now);
    }

    public InterviewRound review(Instant now) {
        return transitionTo(InterviewRoundStatus.REVIEWED, now);
    }

    private InterviewRound transitionTo(InterviewRoundStatus target, Instant now) {
        Objects.requireNonNull(target, "target不能为空");
        Objects.requireNonNull(now, "now不能为空");
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("非法回合状态迁移：" + status + " -> " + target);
        }
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("操作时间不能早于回合更新时间");
        }

        return new InterviewRound(
                roundId, interviewId, ownerId, roundNo, target,
                nextVersion(), createdAt, now,
                target == InterviewRoundStatus.ANSWERED ? now : answeredAt,
                target == InterviewRoundStatus.REVIEWED ? now : null
        );
    }

    private long nextVersion() {
        try {
            return Math.incrementExact(version);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("回合版本超出允许范围", exception);
        }
    }

    private static void validateLifecycle(
            InterviewRoundStatus status,
            Instant createdAt,
            Instant answeredAt,
            Instant reviewedAt
    ) {
        switch (status) {
            case QUESTION_READY -> {
                if (answeredAt != null || reviewedAt != null) {
                    throw new IllegalArgumentException("QUESTION_READY不能包含回答或评审时间");
                }
            }
            case ANSWERED -> {
                if (answeredAt == null || reviewedAt != null) {
                    throw new IllegalArgumentException("ANSWERED必须包含回答时间且不能包含评审时间");
                }
            }
            case REVIEWED -> {
                if (answeredAt == null || reviewedAt == null) {
                    throw new IllegalArgumentException("REVIEWED必须包含回答和评审时间");
                }
            }
        }

        if (answeredAt != null && answeredAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("answeredAt不能早于createdAt");
        }
        if (reviewedAt != null && reviewedAt.isBefore(answeredAt)) {
            throw new IllegalArgumentException("reviewedAt不能早于answeredAt");
        }
    }
}