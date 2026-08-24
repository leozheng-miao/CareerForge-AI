package com.leo.careerforgeai.model.application.reliability;

/**
 * @program: CareerForge-AI
 * @description: 保存当前JVM中的模型可靠性指标快照
 * @author: Miao Zheng
 * @date: 2026-08-24
 * @param logicalCalls 进入重试层的逻辑模型调用数
 * @param retryAttempts 实际执行的额外重试次数
 * @param succeededAfterRetry 重试后成功的逻辑调用数
 * @param failedAfterRetry 重试后仍失败的逻辑调用数
 * @param circuitRejectedCalls 被打开或半开熔断器拒绝的调用数
 */
public record ModelReliabilityMetricsSnapshot(
        long logicalCalls,
        long retryAttempts,
        long succeededAfterRetry,
        long failedAfterRetry,
        long circuitRejectedCalls
) {
}