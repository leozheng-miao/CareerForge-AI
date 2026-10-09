package com.leo.careerforgeai.interviewflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 约束面试心跳、活动租约和过期巡检边界
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Validated
@ConfigurationProperties(
        prefix = "careerforge.interview-flow.timer",
        ignoreUnknownFields = false
)
public record InterviewFlowTimerProperties(
        Duration heartbeatInterval,
        Duration leaseDuration,
        Duration sweepInterval,
        int sweepBatchSize
) {

    public InterviewFlowTimerProperties {
        requirePositive(heartbeatInterval, "heartbeatInterval");
        requirePositive(leaseDuration, "leaseDuration");
        requirePositive(sweepInterval, "sweepInterval");
        if (leaseDuration.compareTo(heartbeatInterval) <= 0) {
            throw new IllegalArgumentException(
                    "leaseDuration必须长于heartbeatInterval"
            );
        }
        if (sweepInterval.compareTo(heartbeatInterval) <= 0) {
            throw new IllegalArgumentException(
                    "sweepInterval必须长于heartbeatInterval"
            );
        }
        if (sweepBatchSize < 1 || sweepBatchSize > 1000) {
            throw new IllegalArgumentException(
                    "sweepBatchSize必须在1到1000之间"
            );
        }
    }

    private static void requirePositive(
            Duration value,
            String fieldName
    ) {
        Objects.requireNonNull(value, fieldName + "不能为null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + "必须大于0");
        }
    }
}
