package com.leo.careerforgeai.agent.application.run.event;

import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 定义允许写入Redis和发送SSE的安全Run事件类型
 * @author: Miao Zheng
 * @date: 2026-08-23
 */
public enum CoachingRunEventType {

    RUN_RECEIVED(false),
    RUN_ACCEPTED(false),
    RUN_STARTED(false),
    TOOL_STARTED(false),
    TOOL_COMPLETED(false),
    ANSWER_READY(false),
    RUN_COMPLETED(true),
    RUN_FAILED(true);

    private final boolean terminal;

    CoachingRunEventType(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public static CoachingRunEventType fromStatus(CoachingRunStatus status) {
        Objects.requireNonNull(status, "status不能为空");
        return switch (status) {
            case RECEIVED -> RUN_RECEIVED;
            case ACCEPTED -> RUN_ACCEPTED;
            case RUNNING -> RUN_STARTED;
            case SUCCEEDED -> RUN_COMPLETED;
            case FAILED, TIMED_OUT, REJECTED, INTERRUPTED -> RUN_FAILED;
        };
    }
}