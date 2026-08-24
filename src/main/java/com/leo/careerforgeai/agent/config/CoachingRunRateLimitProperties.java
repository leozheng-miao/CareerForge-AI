package com.leo.careerforgeai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 配置Coaching Run固定窗口原子限流规则
 * @author: Miao Zheng
 * @date: 2026-08-23
 * @param maxRequests 单个owner在一个窗口内允许创建的新Run数量
 * @param window 固定限流窗口长度
 */
@Validated
@ConfigurationProperties(prefix = "careerforge.agent.run-rate-limit", ignoreUnknownFields = false)
public record CoachingRunRateLimitProperties(
        int maxRequests,
        Duration window
) {

    private static final int MAX_REQUESTS_LIMIT = 100_000;
    private static final Duration MIN_WINDOW = Duration.ofSeconds(1);
    private static final Duration MAX_WINDOW = Duration.ofHours(1);

    public CoachingRunRateLimitProperties {
        Objects.requireNonNull(window, "window不能为空");
        if (maxRequests < 1 || maxRequests > MAX_REQUESTS_LIMIT) {
            throw new IllegalArgumentException("maxRequests必须在1到100000之间");
        }
        if (window.compareTo(MIN_WINDOW) < 0 || window.compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException("window必须在1秒到1小时之间");
        }
    }
}