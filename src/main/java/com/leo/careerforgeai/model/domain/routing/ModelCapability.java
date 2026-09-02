// ModelCapability.java
package com.leo.careerforgeai.model.domain.routing;

/**
 * @program: CareerForge-AI
 * @description: 定义模型和执行Profile能够提供的供应商无关能力。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
public enum ModelCapability {
    CHAT,
    JSON_OBJECT,
    JSON_SCHEMA,
    TOOL_CALLING,
    STREAMING,
    THINKING,
    EMBEDDING,
    RERANK
}