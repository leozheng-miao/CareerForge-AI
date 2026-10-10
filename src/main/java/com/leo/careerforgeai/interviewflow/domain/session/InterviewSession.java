package com.leo.careerforgeai.interviewflow.domain.session;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 保存新面试会话的身份、归属、模式、主阶段和乐观锁版本
 * @author: Miao Zheng
 * @date: 2026-09-30
 * @param interviewId 面试会话ID
 * @param ownerId 面试所属用户
 * @param mode 面试业务模式
 * @param stage 会话主生命周期阶段
 * @param version 聚合乐观锁版本
 */
public record InterviewSession(
        UUID interviewId,
        ActorId ownerId,
        InterviewMode mode,
        SessionStage stage,
        long version
) {

    public InterviewSession {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(mode, "mode不能为空");
        Objects.requireNonNull(stage, "stage不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("version不能小于0");
        }
    }

    public static InterviewSession createQuick(
            UUID interviewId,
            ActorId ownerId
    ) {
        return new InterviewSession(
                interviewId,
                ownerId,
                InterviewMode.QUICK,
                SessionStage.PREPARING,
                0
        );
    }

    public static InterviewSession createComprehensive(UUID interviewId, ActorId ownerId) {
        return new InterviewSession(interviewId, ownerId, InterviewMode.COMPREHENSIVE,
                SessionStage.PREPARING, 0);
    }

    InterviewSession moveTo(SessionStage nextStage) {
        Objects.requireNonNull(nextStage, "nextStage不能为空");
        if (stage.isTerminal()) {
            throw new IllegalStateException("终态面试不能继续迁移");
        }
        return new InterviewSession(
                interviewId,
                ownerId,
                mode,
                nextStage,
                Math.addExact(version, 1)
        );
    }

    InterviewSession incrementVersion() {
        if (stage.isTerminal()) {
            throw new IllegalStateException("终态面试不能继续更新");
        }
        return new InterviewSession(
                interviewId,
                ownerId,
                mode,
                stage,
                Math.addExact(version, 1)
        );
    }
}
