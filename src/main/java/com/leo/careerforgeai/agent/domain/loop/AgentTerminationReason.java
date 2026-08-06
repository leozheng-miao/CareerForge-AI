package com.leo.careerforgeai.agent.domain.loop;

/**
 * @program: CareerForge-AI
 * @description: 记录 Agent Loop 停止运行的确定性原因。
 * @author: Miao Zheng
 * @date: 2026-08-06 17:04
 */
public enum AgentTerminationReason {
    FINAL_ANSWER,
    REFUSAL,
    MAX_MODEL_ITERATIONS,
    MAX_TOTAL_TOOL_CALLS,
    MAX_CALLS_PER_TOOL,
    REPEATED_TOOL_CALL,
    MESSAGE_HISTORY_LIMIT_EXCEEDED,
    TOKEN_BUDGET_EXCEEDED,
    AGENT_DEADLINE_EXCEEDED,
    MODEL_TIMEOUT,
    MODEL_FAILURE,
    INTERRUPTED
}