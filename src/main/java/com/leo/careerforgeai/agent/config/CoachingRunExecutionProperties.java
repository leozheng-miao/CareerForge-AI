package com.leo.careerforgeai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 配置Coaching Run并发容量、执行Deadline和关闭等待时间
 * @author: Miao Zheng
 * @date: 2026-08-20
 * @param maxConcurrentRuns JVM内允许同时执行的Run总数
 * @param maxConcurrentRunsPerOwner 单个owner允许同时执行的Run数
 * @param executionTimeout 单个Run从提交到执行结束的最长时间
 * @param shutdownGracePeriod 应用关闭时等待Run自然结束的最长时间
 **/
@Validated
@ConfigurationProperties(prefix = "careerforge.agent.run", ignoreUnknownFields = false)
public record CoachingRunExecutionProperties(
        int maxConcurrentRuns,
        int maxConcurrentRunsPerOwner,
        Duration executionTimeout,
        Duration shutdownGracePeriod
) {

    private static final int MAX_CONCURRENT_RUNS = 10_000;
    private static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration MAX_EXECUTION_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration MAX_SHUTDOWN_GRACE_PERIOD = Duration.ofMinutes(5);

    public CoachingRunExecutionProperties(
            int maxConcurrentRuns,
            int maxConcurrentRunsPerOwner,
            Duration shutdownGracePeriod
    ) {
        this(maxConcurrentRuns, maxConcurrentRunsPerOwner, DEFAULT_EXECUTION_TIMEOUT, shutdownGracePeriod);
    }

    @ConstructorBinding
    public CoachingRunExecutionProperties {
        Objects.requireNonNull(executionTimeout, "executionTimeout不能为空");
        Objects.requireNonNull(shutdownGracePeriod, "shutdownGracePeriod不能为空");

        if (maxConcurrentRuns < 1 || maxConcurrentRuns > MAX_CONCURRENT_RUNS) {
            throw new IllegalArgumentException("maxConcurrentRuns必须在1到10000之间");
        }
        if (maxConcurrentRunsPerOwner < 1 || maxConcurrentRunsPerOwner > maxConcurrentRuns) {
            throw new IllegalArgumentException("maxConcurrentRunsPerOwner必须在1到maxConcurrentRuns之间");
        }
        if (executionTimeout.isZero()
                || executionTimeout.isNegative()
                || executionTimeout.compareTo(MAX_EXECUTION_TIMEOUT) > 0) {
            throw new IllegalArgumentException("executionTimeout必须大于0且不超过10分钟");
        }
        if (shutdownGracePeriod.isZero()
                || shutdownGracePeriod.isNegative()
                || shutdownGracePeriod.compareTo(MAX_SHUTDOWN_GRACE_PERIOD) > 0) {
            throw new IllegalArgumentException("shutdownGracePeriod必须大于0且不超过5分钟");
        }
    }
}