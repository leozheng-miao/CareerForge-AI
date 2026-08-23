package com.leo.careerforgeai.agent.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Redis命名空间、TTL和Stream长度配置边界
 * @author: Miao Zheng
 * @date: 2026-08-21
 */
class CareerForgeRedisPropertiesTest {

    @Test
    void shouldCreateValidProperties() {
        CareerForgeRedisProperties properties = new CareerForgeRedisProperties(
                "careerforge",
                "test",
                Duration.ofMinutes(30),
                500
        );

        assertThat(properties.namespace()).isEqualTo("careerforge");
        assertThat(properties.environment()).isEqualTo("test");
        assertThat(properties.eventTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.eventStreamMaxLength()).isEqualTo(500);
    }

    @Test
    void shouldRejectUnsafeKeyTokens() {
        assertThatThrownBy(() -> new CareerForgeRedisProperties(
                "careerforge:{unsafe}",
                "test",
                Duration.ofMinutes(30),
                500
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("namespace");

        assertThatThrownBy(() -> new CareerForgeRedisProperties(
                "careerforge",
                "Test",
                Duration.ofMinutes(30),
                500
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("environment");
    }

    @Test
    void shouldRejectInvalidRetentionBoundaries() {
        assertThatThrownBy(() -> new CareerForgeRedisProperties(
                "careerforge",
                "test",
                Duration.ZERO,
                500
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventTtl");

        assertThatThrownBy(() -> new CareerForgeRedisProperties(
                "careerforge",
                "test",
                Duration.ofHours(25),
                500
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventTtl");

        assertThatThrownBy(() -> new CareerForgeRedisProperties(
                "careerforge",
                "test",
                Duration.ofMinutes(30),
                10_001
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventStreamMaxLength");
    }
}