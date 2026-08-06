package com.leo.careerforgeai.agent.domain.loop;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 保存 Agent Loop 的确定性终态、原始最终内容和不可变 Trace。
 * @author: Miao Zheng
 * @date: 2026-08-06 18:00
 **/
public record AgentLoopResult(
        AgentRunStatus status,
        AgentTerminationReason terminationReason,
        String finalContent,
        AgentRunTrace trace
) {

    public AgentLoopResult {
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(terminationReason, "terminationReason 不能为空");
        Objects.requireNonNull(trace, "trace 不能为空");

        if (trace.status() != status) {
            throw new IllegalArgumentException("结果状态与 Trace 状态不一致");
        }
        if (trace.terminationReason() != terminationReason) {
            throw new IllegalArgumentException("结果终止原因与 Trace 终止原因不一致");
        }

        boolean hasFinalContent = finalContent != null && !finalContent.isBlank();
        if ((status == AgentRunStatus.COMPLETED || status == AgentRunStatus.REFUSED) && !hasFinalContent) {
            throw new IllegalArgumentException("完成或拒答结果必须包含 finalContent");
        }
        if (status != AgentRunStatus.COMPLETED && status != AgentRunStatus.REFUSED && finalContent != null) {
            throw new IllegalArgumentException("非完成状态不能包含 finalContent");
        }
        if (!isValidCombination(status, terminationReason)) {
            throw new IllegalArgumentException("Agent 状态与终止原因不匹配");
        }
    }

    /** 创建包含原始模型最终内容的成功结果。 */
    public static AgentLoopResult completed(String finalContent, AgentRunTrace trace) {
        return new AgentLoopResult(
                AgentRunStatus.COMPLETED,
                AgentTerminationReason.FINAL_ANSWER,
                finalContent,
                trace
        );
    }

    /** 创建经过上层结构化校验确认的拒答结果。 */
    public static AgentLoopResult refused(String finalContent, AgentRunTrace trace) {
        return new AgentLoopResult(
                AgentRunStatus.REFUSED,
                AgentTerminationReason.REFUSAL,
                finalContent,
                trace
        );
    }

    /** 创建没有最终模型正文的确定性终止结果。 */
    public static AgentLoopResult terminated(
            AgentRunStatus status,
            AgentTerminationReason terminationReason,
            AgentRunTrace trace
    ) {
        return new AgentLoopResult(status, terminationReason, null, trace);
    }

    /** 校验面向业务的状态与内部终止原因是否属于合法组合。 */
    private static boolean isValidCombination(
            AgentRunStatus status,
            AgentTerminationReason reason
    ) {
        return switch (status) {
            case COMPLETED -> reason == AgentTerminationReason.FINAL_ANSWER;
            case REFUSED -> reason == AgentTerminationReason.REFUSAL;
            case FAILED -> reason == AgentTerminationReason.MODEL_FAILURE
                    || reason == AgentTerminationReason.INTERRUPTED;
            case TIMED_OUT -> reason == AgentTerminationReason.AGENT_DEADLINE_EXCEEDED
                    || reason == AgentTerminationReason.MODEL_TIMEOUT;
            case BUDGET_EXCEEDED -> reason == AgentTerminationReason.TOKEN_BUDGET_EXCEEDED;
            case LIMIT_EXCEEDED -> reason == AgentTerminationReason.MAX_MODEL_ITERATIONS
                    || reason == AgentTerminationReason.MAX_TOTAL_TOOL_CALLS
                    || reason == AgentTerminationReason.MAX_CALLS_PER_TOOL
                    || reason == AgentTerminationReason.REPEATED_TOOL_CALL
                    || reason == AgentTerminationReason.MESSAGE_HISTORY_LIMIT_EXCEEDED;
        };
    }
}