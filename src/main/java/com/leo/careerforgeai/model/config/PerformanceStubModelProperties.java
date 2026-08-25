package com.leo.careerforgeai.model.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 配置性能压测Stub模型的固定阻塞延迟
 * @author: Miao Zheng
 * @date: 2026-08-25
 * @param latency 单次Stub模型调用的固定阻塞时间
 */
@Validated
@Profile("performance-stub")
@ConfigurationProperties(prefix = "careerforge.performance.model-stub", ignoreUnknownFields = false)
public record PerformanceStubModelProperties(Duration latency) {

    private static final Duration MAX_LATENCY = Duration.ofMinutes(1);

    public PerformanceStubModelProperties {
        Objects.requireNonNull(latency, "latency不能为空");
        if (latency.isNegative() || latency.compareTo(MAX_LATENCY) > 0) {
            throw new IllegalArgumentException("latency不能小于0且不能超过1分钟");
        }
    }
}