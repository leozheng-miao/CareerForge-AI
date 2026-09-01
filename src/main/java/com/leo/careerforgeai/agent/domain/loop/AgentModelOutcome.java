package com.leo.careerforgeai.agent.domain.loop;

/**
 * @program: CareerForge-AI
 * @description: 表示某轮模型调用返回最终回答、工具请求或调用失败。
 * @author: Miao Zheng
 * @date: 2026-08-06 17:04
 */
public enum AgentModelOutcome {
    FINAL_ANSWER,
    TOOL_CALLS,
    STRUCTURED_REPAIR,
    FAILURE
}