package com.leo.careerforgeai.agent.api.dto;

import com.leo.careerforgeai.agent.application.run.event.CoachingRunEventType;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 定义客户端可见的安全Run SSE事件名称
 * @author: Miao Zheng
 * @date: 2026-08-23
 */
public enum CoachingRunSseEventType {

    RUN_RECEIVED,
    RUN_ACCEPTED,
    RUN_STARTED,
    TOOL_STARTED,
    TOOL_COMPLETED,
    ANSWER_READY,
    RUN_COMPLETED,
    RUN_FAILED,
    HEARTBEAT;

    public static CoachingRunSseEventType fromEventType(CoachingRunEventType type) {
        Objects.requireNonNull(type, "type不能为空");
        return CoachingRunSseEventType.valueOf(type.name());
    }

    public static CoachingRunSseEventType fromStatus(CoachingRunStatus status) {
        return fromEventType(CoachingRunEventType.fromStatus(status));
    }
}