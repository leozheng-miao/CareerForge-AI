package com.leo.careerforgeai.agent.application.run.ratelimit;

import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureErrorType;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示新高成本Run因Redis限流基础设施不可用而失败关闭
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
public final class CoachingRunRateLimitUnavailableException extends RuntimeException {

    private final ActorId ownerId;
    private final UUID runId;
    private final RedisInfrastructureErrorType errorType;

    public CoachingRunRateLimitUnavailableException(
            ActorId ownerId,
            UUID runId,
            RedisInfrastructureErrorType errorType
    ) {
        super("Coaching Run限流服务暂时不可用");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId不能为空");
        this.runId = Objects.requireNonNull(runId, "runId不能为空");
        this.errorType = Objects.requireNonNull(errorType, "errorType不能为空");
    }

    public ActorId ownerId() {
        return ownerId;
    }

    public UUID runId() {
        return runId;
    }

    public RedisInfrastructureErrorType errorType() {
        return errorType;
    }
}