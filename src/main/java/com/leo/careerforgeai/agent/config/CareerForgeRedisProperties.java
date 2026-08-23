package com.leo.careerforgeai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 配置Redis命名空间、环境隔离和Run事件保留边界
 * @author: Miao Zheng
 * @date: 2026-08-21
 * @param namespace Redis Key统一命名空间
 * @param environment Redis Key运行环境标识
 * @param eventTtl Run过程事件保留时间
 * @param eventStreamMaxLength 单个Run事件Stream允许保留的最大长度
 */
@Validated
@ConfigurationProperties(prefix = "careerforge.redis", ignoreUnknownFields = false)
public record CareerForgeRedisProperties(
        String namespace,
        String environment,
        Duration eventTtl,
        long eventStreamMaxLength
) {

    private static final Pattern KEY_TOKEN_PATTERN = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    private static final Duration MAX_EVENT_TTL = Duration.ofHours(24);
    private static final long MAX_EVENT_STREAM_LENGTH = 10_000;

    public CareerForgeRedisProperties {
        requireKeyToken(namespace, "namespace");
        requireKeyToken(environment, "environment");
        Objects.requireNonNull(eventTtl, "eventTtl不能为空");

        if (eventTtl.isZero() || eventTtl.isNegative() || eventTtl.compareTo(MAX_EVENT_TTL) > 0) {
            throw new IllegalArgumentException("eventTtl必须大于0且不超过24小时");
        }
        if (eventStreamMaxLength < 1 || eventStreamMaxLength > MAX_EVENT_STREAM_LENGTH) {
            throw new IllegalArgumentException("eventStreamMaxLength必须在1到10000之间");
        }
    }

    private static void requireKeyToken(String value, String fieldName) {
        if (value == null || !KEY_TOKEN_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + "必须匹配[a-z][a-z0-9-]{0,31}");
        }
    }
}