package com.leo.careerforgeai.agent.application.run.submission;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示相同owner和requestId被用于不同规范化请求
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
public final class CoachingRunRequestConflictException
        extends RuntimeException {

    private final UUID existingRunId;

    public CoachingRunRequestConflictException(
            UUID existingRunId
    ) {
        super("requestId已被用于不同请求");
        this.existingRunId = existingRunId;
    }

    public UUID existingRunId() {
        return existingRunId;
    }
}