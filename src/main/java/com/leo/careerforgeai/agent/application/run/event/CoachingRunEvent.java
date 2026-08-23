package com.leo.careerforgeai.agent.application.run.event;

import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 表示经过Java白名单校验的安全Run观察事件
 * @author: Miao Zheng
 * @date: 2026-08-23
 * @param ownerId Run所属用户
 * @param runId Run唯一标识
 * @param type 安全事件类型
 * @param status 事件对应的Run状态
 * @param toolName 白名单工具名称，非工具事件为空
 * @param toolStatus 工具完成状态，仅TOOL_COMPLETED使用
 * @param occurredAt 事件发生时间
 */
public record CoachingRunEvent(
        ActorId ownerId,
        UUID runId,
        CoachingRunEventType type,
        CoachingRunStatus status,
        String toolName,
        ToolExecutionStatus toolStatus,
        Instant occurredAt
) {

    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    public CoachingRunEvent {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(runId, "runId不能为空");
        Objects.requireNonNull(occurredAt, "occurredAt不能为空");
        validatePayload(type, status, toolName, toolStatus);
    }

    public static CoachingRunEvent runState(
            ActorId ownerId,
            UUID runId,
            CoachingRunStatus status,
            Instant occurredAt
    ) {
        return new CoachingRunEvent(
                ownerId,
                runId,
                CoachingRunEventType.fromStatus(status),
                status,
                null,
                null,
                occurredAt
        );
    }

    public static CoachingRunEvent toolStarted(
            ActorId ownerId,
            UUID runId,
            String toolName,
            Instant occurredAt
    ) {
        return new CoachingRunEvent(
                ownerId,
                runId,
                CoachingRunEventType.TOOL_STARTED,
                CoachingRunStatus.RUNNING,
                toolName,
                null,
                occurredAt
        );
    }

    public static CoachingRunEvent toolCompleted(
            ActorId ownerId,
            UUID runId,
            String toolName,
            ToolExecutionStatus toolStatus,
            Instant occurredAt
    ) {
        return new CoachingRunEvent(
                ownerId,
                runId,
                CoachingRunEventType.TOOL_COMPLETED,
                CoachingRunStatus.RUNNING,
                toolName,
                toolStatus,
                occurredAt
        );
    }

    public CoachingRunEvent answerReady() {
        if (type != CoachingRunEventType.RUN_COMPLETED) {
            throw new IllegalStateException("只有RUN_COMPLETED可以生成ANSWER_READY");
        }
        return new CoachingRunEvent(
                ownerId,
                runId,
                CoachingRunEventType.ANSWER_READY,
                CoachingRunStatus.SUCCEEDED,
                null,
                null,
                occurredAt
        );
    }

    static void validatePayload(
            CoachingRunEventType type,
            CoachingRunStatus status,
            String toolName,
            ToolExecutionStatus toolStatus
    ) {
        Objects.requireNonNull(type, "type不能为空");
        Objects.requireNonNull(status, "status不能为空");

        switch (type) {
            case RUN_RECEIVED -> requireRunState(status, CoachingRunStatus.RECEIVED, toolName, toolStatus);
            case RUN_ACCEPTED -> requireRunState(status, CoachingRunStatus.ACCEPTED, toolName, toolStatus);
            case RUN_STARTED -> requireRunState(status, CoachingRunStatus.RUNNING, toolName, toolStatus);
            case ANSWER_READY, RUN_COMPLETED ->
                    requireRunState(status, CoachingRunStatus.SUCCEEDED, toolName, toolStatus);
            case RUN_FAILED -> {
                if (!status.isTerminal() || status == CoachingRunStatus.SUCCEEDED) {
                    throw new IllegalArgumentException("RUN_FAILED必须对应失败终态");
                }
                requireNoToolPayload(toolName, toolStatus);
            }
            case TOOL_STARTED -> {
                requireToolName(toolName);
                if (status != CoachingRunStatus.RUNNING) {
                    throw new IllegalArgumentException("TOOL_STARTED必须对应RUNNING");
                }
                if (toolStatus != null) {
                    throw new IllegalArgumentException("TOOL_STARTED不能包含toolStatus");
                }
            }
            case TOOL_COMPLETED -> {
                requireToolName(toolName);
                if (status != CoachingRunStatus.RUNNING) {
                    throw new IllegalArgumentException("TOOL_COMPLETED必须对应RUNNING");
                }
                Objects.requireNonNull(toolStatus, "TOOL_COMPLETED缺少toolStatus");
            }
        }
    }

    private static void requireRunState(
            CoachingRunStatus actual,
            CoachingRunStatus expected,
            String toolName,
            ToolExecutionStatus toolStatus
    ) {
        if (actual != expected) {
            throw new IllegalArgumentException("事件类型与Run状态不一致");
        }
        requireNoToolPayload(toolName, toolStatus);
    }

    private static void requireNoToolPayload(String toolName, ToolExecutionStatus toolStatus) {
        if (toolName != null || toolStatus != null) {
            throw new IllegalArgumentException("非工具事件不能包含工具字段");
        }
    }

    private static void requireToolName(String toolName) {
        if (toolName == null || !TOOL_NAME_PATTERN.matcher(toolName).matches()) {
            throw new IllegalArgumentException("toolName格式不合法");
        }
    }
}