package com.leo.careerforgeai.agent.application.run.execution;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示Coaching Run在开始或执行期间超过服务端Deadline
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
public final class RunExecutionDeadlineExceededException
        extends RuntimeException {

    private final UUID runId;
    private final Instant deadline;

    public RunExecutionDeadlineExceededException(
            UUID runId,
            Instant deadline
    ) {
        super("Coaching Run已经超过执行Deadline");
        this.runId = Objects.requireNonNull(runId, "runId不能为空");
        this.deadline = Objects.requireNonNull(deadline, "deadline不能为空");
    }

    public UUID runId() {
        return runId;
    }

    public Instant deadline() {
        return deadline;
    }
}