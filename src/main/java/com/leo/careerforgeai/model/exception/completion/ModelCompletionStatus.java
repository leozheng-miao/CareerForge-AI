package com.leo.careerforgeai.model.exception.completion;

/**
 * @program: CareerForge-AI
 * @description: 将供应商完成原因映射为供应商无关的稳定完成状态
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
public enum ModelCompletionStatus {

    COMPLETED,
    OUTPUT_TOKEN_LIMIT_REACHED,
    CONTENT_FILTERED,
    TOOL_CALLS_REQUESTED,
    PROVIDER_RESOURCE_INTERRUPTED,
    UNKNOWN_INCOMPLETE
}