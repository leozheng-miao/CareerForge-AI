package com.leo.careerforgeai.agent.application.run.lifecycle;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示Coaching Run乐观锁版本已经发生变化
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
public final class CoachingRunVersionConflictException extends RuntimeException {

    private final UUID runId;
    private final long expectedVersion;

    public CoachingRunVersionConflictException(UUID runId, long expectedVersion) {
        super("Run版本已经发生变化");
        this.runId = Objects.requireNonNull(runId, "runId不能为空");
        this.expectedVersion = expectedVersion;
    }

    public UUID runId() {
        return runId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }
}