package com.leo.careerforgeai.model.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @program: CareerForge-AI
 * @description: 配置单JVM允许同时在途的Tool Calling模型请求数量
 * @author: Miao Zheng
 * @date: 2026-08-24
 * @param maxConcurrentCalls 单JVM模型调用最大并发数
 */
@Validated
@ConfigurationProperties(prefix = "careerforge.model-call-bulkhead", ignoreUnknownFields = false)
public record ModelCallBulkheadProperties(int maxConcurrentCalls) {

    private static final int MAX_CONCURRENT_CALLS = 10_000;

    public ModelCallBulkheadProperties {
        if (maxConcurrentCalls < 1 || maxConcurrentCalls > MAX_CONCURRENT_CALLS) {
            throw new IllegalArgumentException("maxConcurrentCalls必须在1到10000之间");
        }
    }
}