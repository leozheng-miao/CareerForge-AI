package com.leo.careerforgeai.agent.domain.loop;

import com.leo.careerforgeai.model.domain.ModelUsage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 保存一次 Agent Run 在请求内存中的不可变脱敏 Trace 快照。
 * @author: Miao Zheng
 * @date: 2026-08-06 17:04
 */
public record AgentRunTrace(
        String runId,
        Instant startedAt,
        Instant finishedAt,
        AgentRunStatus status,
        AgentTerminationReason terminationReason,
        List<AgentModelCallTrace> modelCalls,
        List<AgentToolCallTrace> toolCalls
) {

    public AgentRunTrace {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId 不能为空");
        Objects.requireNonNull(startedAt, "startedAt 不能为空");
        Objects.requireNonNull(finishedAt, "finishedAt 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(terminationReason, "terminationReason 不能为空");
        if (finishedAt.isBefore(startedAt)) throw new IllegalArgumentException("finishedAt 不能早于 startedAt");
        if (modelCalls == null || modelCalls.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("modelCalls 不能为空且不能包含 null");
        }
        if (toolCalls == null || toolCalls.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("toolCalls 不能为空且不能包含 null");
        }

        modelCalls = List.copyOf(modelCalls);
        toolCalls = List.copyOf(toolCalls);
    }

    public long durationMs() {
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    public ModelUsage totalUsage() {
        long inputTokens = 0;
        long outputTokens = 0;
        long totalTokens = 0;

        for (AgentModelCallTrace trace : modelCalls) {
            if (trace.usage() == null) continue;
            inputTokens = safeAdd(inputTokens, trace.usage().inputTokens());
            outputTokens = safeAdd(outputTokens, trace.usage().outputTokens());
            totalTokens = safeAdd(totalTokens, trace.usage().totalTokens());
        }

        for (AgentToolCallTrace trace : toolCalls) {
            if (trace.modelUsage() == null) continue;
            inputTokens = safeAdd(inputTokens, trace.modelUsage().inputTokens());
            outputTokens = safeAdd(outputTokens, trace.modelUsage().outputTokens());
            totalTokens = safeAdd(totalTokens, trace.modelUsage().totalTokens());
        }

        return new ModelUsage(inputTokens, outputTokens, totalTokens);
    }

    private long safeAdd(long current, long value) {
        try {
            return Math.addExact(current, value);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}