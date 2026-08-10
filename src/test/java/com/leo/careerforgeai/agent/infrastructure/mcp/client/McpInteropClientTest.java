package com.leo.careerforgeai.agent.infrastructure.mcp.client;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.CLOSED;
import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.INITIALIZATION_FAILED;
import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.NOT_INITIALIZED;
import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.TOOL_CALL_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import io.modelcontextprotocol.spec.McpError;

import java.util.concurrent.TimeoutException;

import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.PROTOCOL_INCOMPATIBLE;
import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.TIMEOUT;

/**
 * @program: CareerForge-AI
 * @description: 验证独立MCP Client的显式初始化、工具发现、调用和错误脱敏边界。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
class McpInteropClientTest {

    private McpSyncClient delegate;
    private McpInteropClient client;

    @BeforeEach
    void setUp() {
        delegate = mock(McpSyncClient.class);
        client = new McpInteropClient(delegate);
    }

    @Test
    @DisplayName("未初始化时拒绝tools/list和tools/call且不触发SDK延迟初始化")
    void shouldRejectOperationsBeforeExplicitInitialization() {
        assertThatThrownBy(client::listTools)
                .isInstanceOfSatisfying(McpInteropClientException.class, exception -> {
                    assertThat(exception.errorType()).isEqualTo(NOT_INITIALIZED);
                    assertThat(exception.getMessage()).isEqualTo("MCP Client尚未完成初始化");
                });

        assertThatThrownBy(() ->
                client.callTool("search_career_materials", Map.of("query", "Java"))
        ).isInstanceOfSatisfying(McpInteropClientException.class, exception ->
                assertThat(exception.errorType()).isEqualTo(NOT_INITIALIZED)
        );

        verifyNoInteractions(delegate);
    }

    @Test
    @DisplayName("initialize保留协议版本、能力和服务端实现信息且只执行一次")
    void shouldInitializeAndPreserveNegotiationResult() {
        McpSchema.InitializeResult expected = initializationResult();
        when(delegate.initialize()).thenReturn(expected);

        McpSchema.InitializeResult first = client.initialize();
        McpSchema.InitializeResult second = client.initialize();

        assertThat(first).isSameAs(expected);
        assertThat(second).isSameAs(expected);
        assertThat(client.initializationResult()).isSameAs(expected);
        assertThat(client.isInitialized()).isTrue();
        assertThat(first.protocolVersion()).isEqualTo("2025-06-18");
        assertThat(first.capabilities().tools()).isNotNull();
        assertThat(first.serverInfo().name()).isEqualTo("careerforge-test-mcp");

        verify(delegate, times(1)).initialize();
    }

    @Test
    @DisplayName("初始化后通过tools/list完整返回服务端工具定义")
    void shouldListToolsAfterInitialization() {
        initializeClient();

        McpSchema.Tool tool = McpSchema.Tool.builder(
                        "search_career_materials",
                        Map.of("type", "object")
                )
                .description("搜索职业材料")
                .build();
        McpSchema.ListToolsResult expected =
                McpSchema.ListToolsResult.builder(List.of(tool)).build();

        when(delegate.listTools()).thenReturn(expected);

        McpSchema.ListToolsResult actual = client.listTools();

        assertThat(actual).isSameAs(expected);
        assertThat(actual.tools())
                .extracting(McpSchema.Tool::name)
                .containsExactly("search_career_materials");

        verify(delegate).listTools();
    }

    @Test
    @DisplayName("tools/call保持工具名、参数和服务端isError结果")
    void shouldCallToolAndPreserveProtocolResult() {
        initializeClient();

        McpSchema.CallToolResult expected = McpSchema.CallToolResult.builder()
                .addTextContent("""
                        {"status":"FAILURE","data":null,"error":{"type":"VALIDATION_FAILED","message":"工具参数不合法"}}
                        """)
                .isError(true)
                .build();

        when(delegate.callTool(any())).thenReturn(expected);

        McpSchema.CallToolResult actual = client.callTool(
                "search_career_materials",
                Map.of("query", "")
        );

        assertThat(actual).isSameAs(expected);
        assertThat(actual.isError()).isTrue();

        ArgumentCaptor<McpSchema.CallToolRequest> requestCaptor =
                ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
        verify(delegate).callTool(requestCaptor.capture());

        assertThat(requestCaptor.getValue().name()).isEqualTo("search_career_materials");
        assertThat(requestCaptor.getValue().arguments()).containsEntry("query", "");
    }

    @Test
    @DisplayName("SDK初始化异常转换为固定脱敏错误")
    void shouldSanitizeInitializationFailure() {
        when(delegate.initialize()).thenThrow(
                new IllegalStateException(
                        "Authorization: Bearer secret-token /private/config"
                )
        );

        assertThatThrownBy(client::initialize)
                .isInstanceOfSatisfying(McpInteropClientException.class, exception -> {
                    assertThat(exception.errorType()).isEqualTo(INITIALIZATION_FAILED);
                    assertThat(exception.getMessage())
                            .isEqualTo("MCP Client初始化失败")
                            .doesNotContain(
                                    "secret-token",
                                    "Authorization",
                                    "/private/config",
                                    "IllegalStateException"
                            );
                });

        assertThat(client.isInitialized()).isFalse();
    }

    @Test
    @DisplayName("SDK工具调用异常转换为固定脱敏错误")
    void shouldSanitizeToolCallFailure() {
        initializeClient();

        when(delegate.callTool(any())).thenThrow(
                new IllegalStateException(
                        "apiKey=secret-key internal-host:9200"
                )
        );

        assertThatThrownBy(() ->
                client.callTool("unknown_tool", Map.of())
        ).isInstanceOfSatisfying(McpInteropClientException.class, exception -> {
            assertThat(exception.errorType()).isEqualTo(TOOL_CALL_FAILED);
            assertThat(exception.getMessage())
                    .isEqualTo("MCP工具调用失败")
                    .doesNotContain(
                            "secret-key",
                            "apiKey",
                            "internal-host",
                            "IllegalStateException"
                    );
        });
    }

    @Test
    @DisplayName("关闭Client后释放SDK并拒绝继续使用")
    void shouldCloseDelegateAndRejectFurtherOperations() {
        initializeClient();

        client.close();
        client.close();

        assertThat(client.isInitialized()).isFalse();
        verify(delegate, times(1)).close();

        assertThatThrownBy(client::listTools)
                .isInstanceOfSatisfying(McpInteropClientException.class, exception ->
                        assertThat(exception.errorType()).isEqualTo(CLOSED)
                );
    }

    @Test
    @DisplayName("协议不兼容和超时返回稳定脱敏分类")
    void shouldClassifyProtocolMismatchAndTimeout() {
        McpError protocolError = McpError.builder(McpSchema.ErrorCodes.INVALID_PARAMS)
                .message("Unsupported protocol version")
                .data("Unsupported protocol version from server: secret-version")
                .build();

        when(delegate.initialize()).thenThrow(
                new RuntimeException(
                        "Client failed to initialize with internal configuration",
                        protocolError
                )
        );

        assertThatThrownBy(client::initialize)
                .isInstanceOfSatisfying(McpInteropClientException.class, exception -> {
                    assertThat(exception.errorType()).isEqualTo(PROTOCOL_INCOMPATIBLE);
                    assertThat(exception.getMessage())
                            .isEqualTo("MCP协议版本不兼容")
                            .doesNotContain(
                                    "secret-version",
                                    "internal configuration",
                                    "Unsupported protocol version"
                            );
                });

        McpSyncClient timeoutDelegate = mock(McpSyncClient.class);
        McpInteropClient timeoutClient = new McpInteropClient(timeoutDelegate);

        when(timeoutDelegate.initialize()).thenThrow(
                new RuntimeException(
                        "Client failed to initialize http://internal-host:8081",
                        new TimeoutException("Authorization: Bearer secret-token")
                )
        );

        assertThatThrownBy(timeoutClient::initialize)
                .isInstanceOfSatisfying(McpInteropClientException.class, exception -> {
                    assertThat(exception.errorType()).isEqualTo(TIMEOUT);
                    assertThat(exception.getMessage())
                            .isEqualTo("MCP初始化超时")
                            .doesNotContain(
                                    "internal-host",
                                    "secret-token",
                                    "Authorization",
                                    "TimeoutException"
                            );
                });
    }

    private void initializeClient() {
        when(delegate.initialize()).thenReturn(initializationResult());
        client.initialize();
    }

    private McpSchema.InitializeResult initializationResult() {
        McpSchema.ServerCapabilities capabilities =
                McpSchema.ServerCapabilities.builder()
                        .tools(false)
                        .build();
        McpSchema.Implementation serverInfo =
                McpSchema.Implementation.builder(
                        "careerforge-test-mcp",
                        "test"
                ).build();

        return McpSchema.InitializeResult.builder(
                "2025-06-18",
                capabilities,
                serverInfo
        ).build();
    }
}