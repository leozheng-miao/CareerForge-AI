package com.leo.careerforgeai.agent.domain.loop;

/**
 * @program: CareerForge-AI
 * @description: 表示一次 Agent Run 对上层业务暴露的最终状态。
 * @author: Miao Zheng
 * @date: 2026-08-06 17:04
 */
public enum AgentRunStatus {
    COMPLETED,
    REFUSED,
    FAILED,
    TIMED_OUT,
    BUDGET_EXCEEDED,
    LIMIT_EXCEEDED
}