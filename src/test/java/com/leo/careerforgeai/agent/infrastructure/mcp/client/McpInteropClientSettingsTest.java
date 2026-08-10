package com.leo.careerforgeai.agent.infrastructure.mcp.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证MCP互操作Client只能连接本机地址并使用有界超时。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
class McpInteropClientSettingsTest {

    @Test
    @DisplayName("接受本机Streamable HTTP地址和默认安全参数")
    void shouldAcceptLocalStreamableHttpAddress() {
        McpInteropClientSettings settings =
                McpInteropClientSettings.local(URI.create("http://127.0.0.1:8081"));

        assertThat(settings.baseUri()).isEqualTo(URI.create("http://127.0.0.1:8081"));
        assertThat(settings.endpoint()).isEqualTo("/mcp");
        assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(settings.initializationTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(settings.requestTimeout()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    @DisplayName("拒绝远程主机和HTTPS地址")
    void shouldRejectNonLocalOrNonHttpAddress() {
        assertThatThrownBy(() ->
                McpInteropClientSettings.local(URI.create("http://192.168.1.20:8081"))
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                McpInteropClientSettings.local(URI.create("https://127.0.0.1:8081"))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("拒绝带凭据、业务路径或非法MCP端点的地址")
    void shouldRejectUnsafeAddressComponents() {
        assertThatThrownBy(() ->
                McpInteropClientSettings.local(URI.create("http://user:password@127.0.0.1:8081"))
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                McpInteropClientSettings.local(URI.create("http://127.0.0.1:8081/internal"))
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new McpInteropClientSettings(
                URI.create("http://127.0.0.1:8081"),
                "/../actuator/env",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("拒绝零值、负数和超过上限的超时")
    void shouldRejectInvalidTimeouts() {
        URI localServer = URI.create("http://127.0.0.1:8081");

        assertThatThrownBy(() -> new McpInteropClientSettings(
                localServer,
                "/mcp",
                Duration.ZERO,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new McpInteropClientSettings(
                localServer,
                "/mcp",
                Duration.ofSeconds(1),
                Duration.ofSeconds(-1),
                Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new McpInteropClientSettings(
                localServer,
                "/mcp",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(61)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}