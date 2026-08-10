package com.leo.careerforgeai.agent.infrastructure.mcp.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 使用当前MCP Java SDK 2.0.0装配本机Streamable HTTP同步Client。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
public final class McpInteropClientFactory {

    private static final String CLIENT_NAME = "careerforge-mcp-interop-client";
    private static final String CLIENT_VERSION = "0.0.1";

    private McpInteropClientFactory() {
    }

    /** 创建尚未初始化、无Roots、Sampling或Elicitation能力的独立MCP Client。 */
    public static McpInteropClient create(McpInteropClientSettings settings) {
        Objects.requireNonNull(settings, "settings不能为空");

        HttpClientStreamableHttpTransport transport =
                HttpClientStreamableHttpTransport.builder(settings.baseUri().toString())
                        .endpoint(settings.endpoint())
                        .connectTimeout(settings.connectTimeout())
                        .openConnectionOnStartup(false)
                        .build();

        McpSyncClient sdkClient = McpClient.sync(transport)
                .clientInfo(McpSchema.Implementation.builder(CLIENT_NAME, CLIENT_VERSION).build())
                .capabilities(McpSchema.ClientCapabilities.builder().build())
                .initializationTimeout(settings.initializationTimeout())
                .requestTimeout(settings.requestTimeout())
                .build();

        return new McpInteropClient(sdkClient);
    }
}