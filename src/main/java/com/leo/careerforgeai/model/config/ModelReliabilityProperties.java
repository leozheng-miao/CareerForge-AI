package com.leo.careerforgeai.model.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 配置Tool Calling模型有限重试和熔断策略
 * @author: Miao Zheng
 * @date: 2026-08-24
 * @param maxAttempts 包含首次调用在内的最大尝试次数
 * @param initialBackoff 首次重试前的退避时间
 * @param maxBackoff 最大退避时间
 * @param backoffMultiplier 指数退避倍数
 * @param maxRetryAfter 允许遵守的供应商Retry-After上限
 * @param circuitWindowSize 熔断统计滑动窗口大小
 * @param circuitMinimumCalls 计算失败率前所需的最少调用数
 * @param circuitFailureRateThreshold 打开熔断器的失败率百分比
 * @param circuitOpenDuration 熔断器保持OPEN的时间
 * @param circuitHalfOpenPermittedCalls HALF_OPEN允许的探测调用数
 */
@Validated
@ConfigurationProperties(prefix = "careerforge.model-reliability", ignoreUnknownFields = false)
public record ModelReliabilityProperties(
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff,
        double backoffMultiplier,
        Duration maxRetryAfter,
        int circuitWindowSize,
        int circuitMinimumCalls,
        float circuitFailureRateThreshold,
        Duration circuitOpenDuration,
        int circuitHalfOpenPermittedCalls
) {

    private static final Duration MAX_BACKOFF_LIMIT = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY_AFTER_LIMIT = Duration.ofMinutes(1);
    private static final Duration MAX_OPEN_DURATION = Duration.ofMinutes(5);

    public ModelReliabilityProperties {
        Objects.requireNonNull(initialBackoff, "initialBackoff不能为空");
        Objects.requireNonNull(maxBackoff, "maxBackoff不能为空");
        Objects.requireNonNull(maxRetryAfter, "maxRetryAfter不能为空");
        Objects.requireNonNull(circuitOpenDuration, "circuitOpenDuration不能为空");

        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("maxAttempts必须在1到5之间");
        }
        requirePositiveWithin(initialBackoff, MAX_BACKOFF_LIMIT, "initialBackoff");
        requirePositiveWithin(maxBackoff, MAX_BACKOFF_LIMIT, "maxBackoff");
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff不能小于initialBackoff");
        }
        if (backoffMultiplier < 1.0 || backoffMultiplier > 10.0) {
            throw new IllegalArgumentException("backoffMultiplier必须在1.0到10.0之间");
        }
        requirePositiveWithin(maxRetryAfter, MAX_RETRY_AFTER_LIMIT, "maxRetryAfter");

        if (circuitWindowSize < 2 || circuitWindowSize > 1000) {
            throw new IllegalArgumentException("circuitWindowSize必须在2到1000之间");
        }
        if (circuitMinimumCalls < 1 || circuitMinimumCalls > circuitWindowSize) {
            throw new IllegalArgumentException("circuitMinimumCalls必须在1到circuitWindowSize之间");
        }
        if (circuitFailureRateThreshold < 1.0F || circuitFailureRateThreshold > 100.0F) {
            throw new IllegalArgumentException("circuitFailureRateThreshold必须在1到100之间");
        }
        requirePositiveWithin(circuitOpenDuration, MAX_OPEN_DURATION, "circuitOpenDuration");
        if (circuitHalfOpenPermittedCalls < 1
                || circuitHalfOpenPermittedCalls > circuitWindowSize) {
            throw new IllegalArgumentException(
                    "circuitHalfOpenPermittedCalls必须在1到circuitWindowSize之间"
            );
        }
    }

    private static void requirePositiveWithin(
            Duration value,
            Duration maximum,
            String field
    ) {
        if (value.isZero() || value.isNegative() || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    field + "必须大于0且不超过" + maximum
            );
        }
    }
}