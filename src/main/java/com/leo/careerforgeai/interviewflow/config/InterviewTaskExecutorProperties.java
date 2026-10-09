package com.leo.careerforgeai.interviewflow.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @program: CareerForge-AI
 * @description: 限制面试后台任务轮询、租约、并发、超时和重试
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Validated
@ConfigurationProperties(
        prefix = "careerforge.interview-flow.task-executor",
        ignoreUnknownFields = false
)
public record InterviewTaskExecutorProperties(
        boolean enabled,
        @Min(1) @Max(100) int pollBatchSize,
        @Min(1) @Max(256) int maxConcurrentTasks,
        @Min(1) @Max(16) int maxConcurrentTasksPerOwner,
        @NotNull Duration pollInterval,
        @NotNull Duration leaseDuration,
        @NotNull Duration taskTimeout,
        @NotNull Duration retryDelay
) {

    public InterviewTaskExecutorProperties {
        requirePositive(pollInterval, "pollInterval");
        requirePositive(leaseDuration, "leaseDuration");
        requirePositive(taskTimeout, "taskTimeout");
        requirePositive(retryDelay, "retryDelay");
        if (maxConcurrentTasksPerOwner > maxConcurrentTasks) {
            throw new IllegalArgumentException(
                    "单owner并发不能超过全局并发"
            );
        }
        if (leaseDuration.compareTo(taskTimeout) <= 0) {
            throw new IllegalArgumentException(
                    "leaseDuration必须大于taskTimeout"
            );
        }
    }

    private static void requirePositive(Duration value, String fieldName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + "必须大于0");
        }
    }
}
