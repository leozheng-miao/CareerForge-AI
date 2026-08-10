package com.leo.careerforgeai.agent.infrastructure.springai.tool;

/**
 * @program: CareerForge-AI
 * @description: 分类Spring AI默认工具循环触发的服务端运行限制。
 * @author: Miao Zheng
 * @date: 2026-08-10 05:00
 **/
public enum SpringAiToolLoopLimitType {
    MAX_MODEL_ITERATIONS,
    MAX_TOTAL_TOOL_CALLS,
    MAX_CALLS_PER_TOOL,
    REPEATED_TOOL_CALL,
    DEADLINE_EXCEEDED
}