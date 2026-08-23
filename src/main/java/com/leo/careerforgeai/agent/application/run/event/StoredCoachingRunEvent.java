package com.leo.careerforgeai.agent.application.run.event;

import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 表示从Redis Stream严格还原的安全Run事件
 * @author: Miao Zheng
 * @date: 2026-08-23
 * @param eventId Redis Stream事件ID
 * @param runId Run唯一标识
 * @param type 安全事件类型
 * @param status 事件对应的Run状态
 * @param toolName 白名单工具名称
 * @param toolStatus 工具完成状态
 * @param occurredAt 事件发生时间
 */
public record StoredCoachingRunEvent(
        String eventId,
        UUID runId,
        CoachingRunEventType type,
        CoachingRunStatus status,
        String toolName,
        ToolExecutionStatus toolStatus,
        Instant occurredAt
) {

    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("\\d+-\\d+");

    public StoredCoachingRunEvent {
        if (eventId == null || !EVENT_ID_PATTERN.matcher(eventId).matches()) {
            throw new IllegalArgumentException("eventId格式不合法");
        }
        Objects.requireNonNull(runId, "runId不能为空");
        Objects.requireNonNull(occurredAt, "occurredAt不能为空");
        CoachingRunEvent.validatePayload(type, status, toolName, toolStatus);
    }
}