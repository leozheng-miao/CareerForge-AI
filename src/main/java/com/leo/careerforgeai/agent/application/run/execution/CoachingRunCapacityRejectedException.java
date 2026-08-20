package com.leo.careerforgeai.agent.application.run.execution;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 表示全局或owner Coaching Run执行容量已经耗尽
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
public final class CoachingRunCapacityRejectedException extends RuntimeException {

    private final ActorId ownerId;

    public CoachingRunCapacityRejectedException(ActorId ownerId) {
        super("Coaching Run执行容量已满");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId不能为空");
    }

    public ActorId ownerId() {
        return ownerId;
    }
}