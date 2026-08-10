package com.leo.careerforgeai.agent.application.loop;

import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentModelCallTrace;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentRunTrace;
import com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentToolCallTrace;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @program: CareerForge-AI
 * @description: 维护单次 Agent Run 的迭代、工具调用、重复指纹、Token 和 Trace 状态。
 * @author: Miao Zheng
 * @date: 2026-08-06 17:28
 **/
public final class AgentRunState {

    private final String runId;
    private final AgentLoopPolicy policy;
    private final Instant startedAt;
    private final Instant deadline;
    private final List<AgentModelCallTrace> modelCallTraces = new ArrayList<>();
    private final List<AgentToolCallTrace> toolCallTraces = new ArrayList<>();
    private final Map<String, Integer> callsByToolName = new HashMap<>();
    private final Map<String, Integer> callsByFingerprint = new HashMap<>();
    private final List<ToolExecutionResult> toolResults = new ArrayList<>();

    private int modelIterations;
    private int totalToolCalls;
    private long inputTokens;
    private long outputTokens;
    private long totalTokens;

    public AgentRunState(String runId, AgentLoopPolicy policy, Instant startedAt) {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId 不能为空");
        this.runId = runId;
        this.policy = Objects.requireNonNull(policy, "policy 不能为空");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt 不能为空");
        this.deadline = startedAt.plus(policy.totalTimeout());
    }

    /** 在模型调用前检查 Deadline、迭代、消息长度和 Token 软预算。 */
    public Optional<AgentTerminationReason> checkBeforeModelCall(
            long estimatedInputTokens,
            int messageHistoryChars,
            Instant now
    ) {
        if (estimatedInputTokens < 0) throw new IllegalArgumentException("estimatedInputTokens 不能小于 0");
        if (messageHistoryChars < 0) throw new IllegalArgumentException("messageHistoryChars 不能小于 0");
        Objects.requireNonNull(now, "now 不能为空");

        if (!now.isBefore(deadline)) return Optional.of(AgentTerminationReason.AGENT_DEADLINE_EXCEEDED);
        if (modelIterations >= policy.maxModelIterations()) {
            return Optional.of(AgentTerminationReason.MAX_MODEL_ITERATIONS);
        }
        if (messageHistoryChars > policy.maxMessageHistoryChars()) {
            return Optional.of(AgentTerminationReason.MESSAGE_HISTORY_LIMIT_EXCEEDED);
        }

        long estimatedCallTokens = safeAdd(estimatedInputTokens, policy.maxOutputTokensPerModelCall());
        long projectedTotalTokens = safeAdd(totalTokens, estimatedCallTokens);
        if (totalTokens >= policy.maxTotalTokens() || projectedTotalTokens > policy.maxTotalTokens()) {
            return Optional.of(AgentTerminationReason.TOKEN_BUDGET_EXCEEDED);
        }

        return Optional.empty();
    }

    /** 开始一轮已经通过前置检查的模型调用并返回新的迭代序号。 */
    public int startModelIteration() {
        if (modelIterations >= policy.maxModelIterations()) {
            throw new IllegalStateException("模型迭代次数已经达到上限");
        }
        return ++modelIterations;
    }

    /** 原子校验并登记当前模型轮次产生的全部 Tool Calls。 */
    public Optional<AgentTerminationReason> registerToolCalls(
            List<ToolCall> toolCalls,
            ToolCallFingerprintService fingerprintService,
            String contextVersion
    ) {
        if (toolCalls == null || toolCalls.isEmpty()) throw new IllegalArgumentException("toolCalls 不能为空");
        if (toolCalls.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("toolCalls 不能包含 null");
        }
        Objects.requireNonNull(fingerprintService, "fingerprintService 不能为空");

        if ((long) totalToolCalls + toolCalls.size() > policy.maxTotalToolCalls()) {
            return Optional.of(AgentTerminationReason.MAX_TOTAL_TOOL_CALLS);
        }

        Map<String, Integer> projectedToolCounts = new HashMap<>(callsByToolName);
        Map<String, Integer> projectedFingerprintCounts = new HashMap<>(callsByFingerprint);

        for (ToolCall toolCall : toolCalls) {
            int toolCount = projectedToolCounts.merge(toolCall.name(), 1, Integer::sum);
            if (toolCount > policy.maxCallsPerTool()) {
                return Optional.of(AgentTerminationReason.MAX_CALLS_PER_TOOL);
            }

            String fingerprint = fingerprintService.fingerprint(toolCall, contextVersion);
            int fingerprintCount = projectedFingerprintCounts.merge(fingerprint, 1, Integer::sum);
            if (fingerprintCount > policy.maxRepeatedCallCount()) {
                return Optional.of(AgentTerminationReason.REPEATED_TOOL_CALL);
            }
        }

        callsByToolName.clear();
        callsByToolName.putAll(projectedToolCounts);
        callsByFingerprint.clear();
        callsByFingerprint.putAll(projectedFingerprintCounts);
        totalToolCalls += toolCalls.size();
        return Optional.empty();
    }

    /** 记录一轮模型调用 Trace，并累计供应商返回的真实 Token。 */
    public void recordModelCall(AgentModelCallTrace trace) {
        Objects.requireNonNull(trace, "trace 不能为空");
        if (trace.iteration() != modelIterations) {
            throw new IllegalArgumentException("模型 Trace iteration 与当前迭代不一致");
        }

        modelCallTraces.add(trace);
        addUsage(trace.usage());
    }

    /** 记录一次工具执行 Trace，并累计 MODEL_BACKED 工具内部 Token。 */
    public void recordToolCall(AgentToolCallTrace trace) {
        Objects.requireNonNull(trace, "trace 不能为空");
        if (trace.iteration() != modelIterations) {
            throw new IllegalArgumentException("工具 Trace iteration 与当前迭代不一致");
        }

        toolCallTraces.add(trace);
        addUsage(trace.modelUsage());
    }

    /** 判断真实累计 Token 是否已经达到预算边界。 */
    public boolean isTokenBudgetExhausted() {
        return totalTokens >= policy.maxTotalTokens();
    }

    /** 根据 Agent 剩余时间收紧本轮模型调用超时。 */
    public Duration modelCallTimeout(Instant now) {
        Duration remaining = remainingTime(now);
        return policy.modelCallTimeout().compareTo(remaining) <= 0
                ? policy.modelCallTimeout()
                : remaining;
    }

    /** 返回指定时刻的 Agent 剩余时间，过期时返回零。 */
    public Duration remainingTime(Instant now) {
        Objects.requireNonNull(now, "now 不能为空");
        Duration remaining = Duration.between(now, deadline);
        return remaining.isNegative() || remaining.isZero() ? Duration.ZERO : remaining;
    }

    /** 创建本次运行结束时的不可变 Trace 快照。 */
    public AgentRunTrace snapshot(
            AgentRunStatus status,
            AgentTerminationReason terminationReason,
            Instant finishedAt
    ) {
        return new AgentRunTrace(runId, startedAt, finishedAt, status, terminationReason,
                modelCallTraces, toolCallTraces);
    }

    /** 返回当前模型迭代次数。 */
    public int modelIterations() {
        return modelIterations;
    }

    /** 返回已经登记的工具调用总数。 */
    public int totalToolCalls() {
        return totalToolCalls;
    }

    /** 返回当前累计的真实模型 Token。 */
    public ModelUsage totalUsage() {
        return new ModelUsage(inputTokens, outputTokens, totalTokens);
    }

    /** 按工具执行顺序保存供上层业务校验使用的内部Tool Result。 */
    public void recordToolResult(ToolExecutionResult result) {
        Objects.requireNonNull(result, "result 不能为空");

        int resultIndex = toolResults.size();
        if (resultIndex >= toolCallTraces.size()) {
            throw new IllegalStateException("Tool Result缺少对应的Tool Trace");
        }

        AgentToolCallTrace trace = toolCallTraces.get(resultIndex);
        if (!trace.toolCallId().equals(result.toolCallId())
                || !trace.toolName().equals(result.toolName())
                || trace.status() != result.status()
                || trace.errorType() != result.errorType()) {
            throw new IllegalArgumentException("Tool Result与Tool Trace无法关联");
        }

        toolResults.add(result);
    }

    /** 返回本次Agent Run中按执行顺序保存的内部Tool Result快照。 */
    public List<ToolExecutionResult> toolResults() {
        return List.copyOf(toolResults);
    }

    /** 返回本次 Agent Run 的统一 Deadline。 */
    public Instant deadline() {
        return deadline;
    }

    /** 累加一次可观察模型调用的 Token。 */
    private void addUsage(ModelUsage usage) {
        if (usage == null) return;
        inputTokens = safeAdd(inputTokens, usage.inputTokens());
        outputTokens = safeAdd(outputTokens, usage.outputTokens());
        totalTokens = safeAdd(totalTokens, usage.totalTokens());
    }

    /** 使用饱和加法避免恶意或异常 Token 数值导致 long 溢出回绕。 */
    private long safeAdd(long current, long value) {
        try {
            return Math.addExact(current, value);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}