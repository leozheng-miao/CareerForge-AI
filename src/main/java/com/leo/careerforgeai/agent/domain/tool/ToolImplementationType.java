package com.leo.careerforgeai.agent.domain.tool;

/**
 * @program: CareerForge-AI
 * @description: 标识工具的实现方式，用于执行策略、Trace和成本统计。
 * @author: Miao Zheng
 * @date: 2026-08-06 21:20
 **/
public enum ToolImplementationType {
    DETERMINISTIC,
    RETRIEVAL_BACKED,
    MODEL_BACKED,
    MCP_REMOTE
}