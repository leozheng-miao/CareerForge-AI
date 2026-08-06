package com.leo.careerforgeai.agent.domain.tool;

/** 标识工具的实现方式，用于执行策略、Trace 和成本统计。 */
public enum ToolImplementationType {
    DETERMINISTIC,
    MODEL_BACKED,
    MCP_REMOTE
}