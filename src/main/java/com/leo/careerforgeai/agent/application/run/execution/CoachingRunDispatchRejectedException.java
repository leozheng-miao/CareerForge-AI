package com.leo.careerforgeai.agent.application.run.execution;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示虚拟线程执行器关闭后拒绝接受新的Coaching Run
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
public final class CoachingRunDispatchRejectedException extends RuntimeException {

    private final ActorId ownerId;
    private final UUID runId;

    public CoachingRunDispatchRejectedException(ActorId ownerId, UUID runId, Throwable cause) {
        super("Coaching Run执行器已经停止接收任务", cause);
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId不能为空");
        this.runId = Objects.requireNonNull(runId, "runId不能为空");
    }

    public ActorId ownerId() {
        return ownerId;
    }

    public UUID runId() {
        return runId;
    }
}