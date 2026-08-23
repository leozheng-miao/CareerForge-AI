package com.leo.careerforgeai.agent.api.dto;

import com.leo.careerforgeai.agent.application.run.event.CoachingRunEventType;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回不包含owner、Prompt、工具参数、工具结果和内部异常的安全Run事件
 * @author: Miao Zheng
 * @date: 2026-08-23
 * @param runId Run唯一标识
 * @param type 安全事件类型
 * @param status 事件对应的Run状态
 * @param toolName 白名单工具名称
 * @param toolStatus 工具完成状态
 * @param source 事件事实来源
 * @param occurredAt 事件发生时间
 */
public record CoachingRunSseEventResponse(
        UUID runId,
        CoachingRunEventType type,
        CoachingRunStatus status,
        String toolName,
        ToolExecutionStatus toolStatus,
        CoachingRunSseEventSource source,
        Instant occurredAt
) {

    public CoachingRunSseEventResponse {
        Objects.requireNonNull(runId, "runId不能为空");
        Objects.requireNonNull(type, "type不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(source, "source不能为空");
        Objects.requireNonNull(occurredAt, "occurredAt不能为空");

        boolean toolEvent = type == CoachingRunEventType.TOOL_STARTED
                || type == CoachingRunEventType.TOOL_COMPLETED;
        if (toolEvent != (toolName != null)) {
            throw new IllegalArgumentException("工具事件与toolName不一致");
        }
        if (type == CoachingRunEventType.TOOL_COMPLETED && toolStatus == null) {
            throw new IllegalArgumentException("TOOL_COMPLETED缺少toolStatus");
        }
        if (type != CoachingRunEventType.TOOL_COMPLETED && toolStatus != null) {
            throw new IllegalArgumentException("当前事件不能包含toolStatus");
        }
        if (source == CoachingRunSseEventSource.MYSQL_TERMINAL_SNAPSHOT && !type.isTerminal()) {
            throw new IllegalArgumentException("MySQL快照只能发送终态事件");
        }
    }
}