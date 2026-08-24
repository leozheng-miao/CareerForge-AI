package com.leo.careerforgeai.model.application.reliability;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.LongAdder;

/**
 * @program: CareerForge-AI
 * @description: 独立统计模型内部重试和熔断拒绝且不修改Run聚合
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
@Component
public class ModelReliabilityMetrics {

    private final LongAdder logicalCalls = new LongAdder();
    private final LongAdder retryAttempts = new LongAdder();
    private final LongAdder succeededAfterRetry = new LongAdder();
    private final LongAdder failedAfterRetry = new LongAdder();
    private final LongAdder circuitRejectedCalls = new LongAdder();

    void recordLogicalCall() {
        logicalCalls.increment();
    }

    void recordRetryAttempt() {
        retryAttempts.increment();
    }

    void recordSucceededAfterRetry() {
        succeededAfterRetry.increment();
    }

    void recordFailedAfterRetry() {
        failedAfterRetry.increment();
    }

    void recordCircuitRejected() {
        circuitRejectedCalls.increment();
    }

    public ModelReliabilityMetricsSnapshot snapshot() {
        return new ModelReliabilityMetricsSnapshot(
                logicalCalls.sum(),
                retryAttempts.sum(),
                succeededAfterRetry.sum(),
                failedAfterRetry.sum(),
                circuitRejectedCalls.sum()
        );
    }
}