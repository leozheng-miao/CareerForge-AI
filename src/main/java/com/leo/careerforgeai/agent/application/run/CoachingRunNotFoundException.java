package com.leo.careerforgeai.agent.application.run;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示当前用户无法读取指定Coaching Run
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
public final class CoachingRunNotFoundException extends RuntimeException {

    private final UUID runId;

    public CoachingRunNotFoundException(UUID runId) {
        super("Run不存在或不属于当前用户");
        this.runId = Objects.requireNonNull(runId, "runId不能为空");
    }

    public UUID runId() {
        return runId;
    }
}