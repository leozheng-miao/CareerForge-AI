package com.leo.careerforgeai.agent.application.run.ratelimit;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示新Coaching Run超过owner固定窗口限流
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
public final class CoachingRunRateLimitExceededException extends RuntimeException {

    private final ActorId ownerId;
    private final UUID runId;
    private final Duration retryAfter;

    public CoachingRunRateLimitExceededException(ActorId ownerId, UUID runId, Duration retryAfter) {
        super("Coaching Run请求频率超过限制");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId不能为空");
        this.runId = Objects.requireNonNull(runId, "runId不能为空");
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter不能为空");
        if (retryAfter.isZero() || retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter必须大于0");
        }
    }

    public ActorId ownerId() {
        return ownerId;
    }

    public UUID runId() {
        return runId;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}