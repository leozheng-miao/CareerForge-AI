package com.leo.careerforgeai.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 定义单个来源与邮箱组合的登录固定窗口限制
 * @author: Miao Zheng
 * @date: 2026-09-03
 * @param maxAttempts 单个窗口允许的登录次数
 * @param window 固定窗口长度
 **/
@ConfigurationProperties(prefix = "careerforge.login-rate-limit", ignoreUnknownFields = false)
public record AuthLoginRateLimitProperties(int maxAttempts, Duration window) {

    public AuthLoginRateLimitProperties {
        Objects.requireNonNull(window, "window不能为空");
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalArgumentException("maxAttempts必须在1到100之间");
        }
        if (window.compareTo(Duration.ofMinutes(1)) < 0
                || window.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("window必须在1分钟到1小时之间");
        }
    }
}