package com.leo.careerforgeai.agent.infrastructure.mcp.client;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 定义仅允许连接本机MCP Server的互操作Client连接参数和超时边界。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
public record McpInteropClientSettings(
        URI baseUri,
        String endpoint,
        Duration connectTimeout,
        Duration initializationTimeout,
        Duration requestTimeout
) {

    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(60);
    private static final Set<String> LOOPBACK_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "::1",
            "[::1]",
            "0:0:0:0:0:0:0:1"
    );

    public McpInteropClientSettings {
        Objects.requireNonNull(baseUri, "baseUri不能为空");
        endpoint = Objects.requireNonNull(endpoint, "endpoint不能为空").trim();
        connectTimeout = validateTimeout("connectTimeout", connectTimeout);
        initializationTimeout = validateTimeout("initializationTimeout", initializationTimeout);
        requestTimeout = validateTimeout("requestTimeout", requestTimeout);
        validateBaseUri(baseUri);
        validateEndpoint(endpoint);
    }

    /** 创建使用/mcp端点和受限超时的本机互操作配置。 */
    public static McpInteropClientSettings local(URI baseUri) {
        return new McpInteropClientSettings(
                baseUri,
                "/mcp",
                Duration.ofSeconds(3),
                Duration.ofSeconds(5),
                Duration.ofSeconds(15)
        );
    }

    /** 拒绝远程主机、凭据、查询参数和非HTTP地址。 */
    private static void validateBaseUri(URI baseUri) {
        if (!"http".equalsIgnoreCase(baseUri.getScheme())) {
            throw new IllegalArgumentException("MCP互操作实验只允许本机HTTP地址");
        }

        String host = baseUri.getHost();
        if (host == null || !LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("MCP互操作实验只允许连接本机地址");
        }

        if (baseUri.getUserInfo() != null || baseUri.getQuery() != null || baseUri.getFragment() != null) {
            throw new IllegalArgumentException("MCP baseUri不得包含凭据、查询参数或Fragment");
        }

        String path = baseUri.getPath();
        if (path != null && !path.isBlank() && !"/".equals(path)) {
            throw new IllegalArgumentException("MCP baseUri不得包含业务路径");
        }
    }

    /** 保证Streamable HTTP端点是单独、固定的绝对路径。 */
    private static void validateEndpoint(String endpoint) {
        if (endpoint.isBlank()
                || !endpoint.startsWith("/")
                || endpoint.contains("..")
                || endpoint.contains("\\")
                || endpoint.contains("?")
                || endpoint.contains("#")) {
            throw new IllegalArgumentException("MCP endpoint不是合法的绝对路径");
        }
    }

    /** 拒绝无界、零值和负数超时。 */
    private static Duration validateTimeout(String name, Duration timeout) {
        Objects.requireNonNull(timeout, name + "不能为空");

        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException(name + "必须大于0且不超过60秒");
        }

        return timeout;
    }
}