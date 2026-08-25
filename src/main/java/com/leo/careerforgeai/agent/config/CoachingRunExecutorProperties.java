package com.leo.careerforgeai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 配置Coaching Run执行器模式和平台线程池大小
 * @author: Miao Zheng
 * @date: 2026-08-25
 * @param mode Run执行器模式
 * @param platformThreadCount PLATFORM模式的固定线程数
 */
@Validated
@ConfigurationProperties(prefix = "careerforge.agent.run-executor", ignoreUnknownFields = false)
public record CoachingRunExecutorProperties(
        CoachingRunExecutorMode mode,
        int platformThreadCount
) {

    private static final int MAX_PLATFORM_THREADS = 10_000;

    public CoachingRunExecutorProperties {
        Objects.requireNonNull(mode, "mode不能为空");
        if (platformThreadCount < 1 || platformThreadCount > MAX_PLATFORM_THREADS) {
            throw new IllegalArgumentException("platformThreadCount必须在1到10000之间");
        }
    }
}