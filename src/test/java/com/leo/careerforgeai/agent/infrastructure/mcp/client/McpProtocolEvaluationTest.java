package com.leo.careerforgeai.agent.infrastructure.mcp.client;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.PROTOCOL_INCOMPATIBLE;
import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.TIMEOUT;
import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.TOOL_CALL_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 使用固定输入聚合评测MCP Client的八项协议行为，不依赖外部MCP进程。
 * @author: Miao Zheng
 * @date: 2026-08-11
 **/
class McpProtocolEvaluationTest {

    @Test
    @DisplayName("固定MCP协议Case一次执行并输出各项结果")
    void shouldEvaluateFixedMcpProtocolCases() {
        List<ProtocolCaseResult> results = List.of(
                runCase("mcp-eval-001", "Initialization Success", this::verifyInitialization),
                runCase("mcp-eval-002", "Capability Negotiation", this::verifyCapabilityNegotiation),
                runCase("mcp-eval-003", "Tool Discovery", this::verifyToolDiscovery),
                runCase("mcp-eval-004", "Argument Transport", this::verifyArgumentTransport),
                runCase("mcp-eval-005", "Tool Call", this::verifyToolCall),
                runCase("mcp-eval-006", "Error Mapping", this::verifyErrorMapping),
                runCase("mcp-eval-007", "Timeout Mapping", this::verifyTimeoutMapping),
                runCase("mcp-eval-008", "Protocol Compatibility", this::verifyProtocolCompatibility)
        );

        printReport(results);

        List<String> failedCaseIds = results.stream()
                .filter(result -> !result.passed())
                .map(ProtocolCaseResult::caseId)
                .toList();

        assertThat(results).hasSize(8);
        assertThat(failedCaseIds)
                .as("MCP协议评测失败Case")
                .isEmpty();
    }

    private void verifyInitialization() {
        McpSyncClient delegate = mock(McpSyncClient.class);
        McpSchema.InitializeResult expected = initializationResult();
        when(delegate.initialize()).thenReturn(expected);

        McpInteropClient client = new McpInteropClient(delegate);
        McpSchema.InitializeResult actual = client.initialize();

        assertThat(actual).isSameAs(expected);
        assertThat(client.isInitialized()).isTrue();
        assertThat(client.initializationResult()).isSameAs(expected);
        verify(delegate).initialize();
    }

    private void verifyCapabilityNegotiation() {
        McpSyncClient delegate = mock(McpSyncClient.class);
        when(delegate.initialize()).thenReturn(initializationResult());

        McpSchema.InitializeResult result = new McpInteropClient(delegate).initialize();

        assertThat(result.protocolVersion()).isEqualTo("2025-06-18");
        assertThat(result.capabilities().tools()).isNotNull();
        assertThat(result.serverInfo().name()).isEqualTo("careerforge-test-mcp");
        assertThat(result.serverInfo().version()).isEqualTo("0.0.1");
    }

    private void verifyToolDiscovery() {
        McpSyncClient delegate = mock(McpSyncClient.class);
        McpInteropClient client = initializedClient(delegate);
        McpSchema.Tool tool = McpSchema.Tool.builder(
                        "search_career_materials",
                        Map.of("type", "object")
                )
                .description("搜索职业材料")
                .build();
        when(delegate.listTools()).thenReturn(
                McpSchema.ListToolsResult.builder(List.of(tool)).build()
        );

        McpSchema.ListToolsResult result = client.listTools();

        assertThat(result.tools())
                .extracting(McpSchema.Tool::name)
                .containsExactly("search_career_materials");
        verify(delegate).listTools();
    }

    private void verifyArgumentTransport() {
        McpSyncClient delegate = mock(McpSyncClient.class);
        McpInteropClient client = initializedClient(delegate);
        when(delegate.callTool(any())).thenReturn(successCallResult());

        client.callTool(
                "search_career_materials",
                Map.of("query", "Java Agent岗位要求", "topK", 3)
        );

        ArgumentCaptor<McpSchema.CallToolRequest> captor =
                ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
        verify(delegate).callTool(captor.capture());

        McpSchema.CallToolRequest request = captor.getValue();
        assertThat(request.name()).isEqualTo("search_career_materials");
        assertThat(request.arguments())
                .containsEntry("query", "Java Agent岗位要求")
                .containsEntry("topK", 3);
    }

    private void verifyToolCall() {
        McpSyncClient delegate = mock(McpSyncClient.class);
        McpInteropClient client = initializedClient(delegate);
        McpSchema.CallToolResult expected = successCallResult();
        when(delegate.callTool(any())).thenReturn(expected);

        McpSchema.CallToolResult actual = client.callTool(
                "search_career_materials",
                Map.of("query", "Java Agent岗位要求")
        );

        assertThat(actual).isSameAs(expected);
        assertThat(actual.isError()).isFalse();
    }

    private void verifyErrorMapping() {
        McpSyncClient delegate = mock(McpSyncClient.class);
        McpInteropClient client = initializedClient(delegate);
        when(delegate.callTool(any())).thenThrow(
                new IllegalStateException("apiKey=secret-key internal-host:9200")
        );

        assertThatThrownBy(() ->
                client.callTool("search_career_materials", Map.of("query", "Java"))
        ).isInstanceOfSatisfying(McpInteropClientException.class, exception -> {
            assertThat(exception.errorType()).isEqualTo(TOOL_CALL_FAILED);
            assertThat(exception.getMessage())
                    .isEqualTo("MCP工具调用失败")
                    .doesNotContain("secret-key", "apiKey", "internal-host");
        });
    }

    private void verifyTimeoutMapping() {
        McpSyncClient delegate = mock(McpSyncClient.class);
        McpInteropClient client = initializedClient(delegate);
        when(delegate.callTool(any())).thenThrow(
                new RuntimeException(
                        "request to internal-host timed out",
                        new TimeoutException("Authorization: Bearer secret-token")
                )
        );

        assertThatThrownBy(() ->
                client.callTool("search_career_materials", Map.of("query", "Java"))
        ).isInstanceOfSatisfying(McpInteropClientException.class, exception -> {
            assertThat(exception.errorType()).isEqualTo(TIMEOUT);
            assertThat(exception.getMessage())
                    .isEqualTo("MCP请求超时")
                    .doesNotContain("internal-host", "secret-token", "Authorization");
        });
    }

    private void verifyProtocolCompatibility() {
        McpSyncClient delegate = mock(McpSyncClient.class);
        McpError protocolError = McpError.builder(McpSchema.ErrorCodes.INVALID_PARAMS)
                .message("Unsupported protocol version")
                .data("secret-version")
                .build();
        when(delegate.initialize()).thenThrow(
                new RuntimeException("internal protocol configuration", protocolError)
        );

        McpInteropClient client = new McpInteropClient(delegate);

        assertThatThrownBy(client::initialize)
                .isInstanceOfSatisfying(McpInteropClientException.class, exception -> {
                    assertThat(exception.errorType()).isEqualTo(PROTOCOL_INCOMPATIBLE);
                    assertThat(exception.getMessage())
                            .isEqualTo("MCP协议版本不兼容")
                            .doesNotContain(
                                    "secret-version",
                                    "internal protocol configuration",
                                    "Unsupported protocol version"
                            );
                });
    }

    private McpInteropClient initializedClient(McpSyncClient delegate) {
        when(delegate.initialize()).thenReturn(initializationResult());
        McpInteropClient client = new McpInteropClient(delegate);
        client.initialize();
        return client;
    }

    private McpSchema.InitializeResult initializationResult() {
        return McpSchema.InitializeResult.builder(
                "2025-06-18",
                McpSchema.ServerCapabilities.builder().tools(false).build(),
                McpSchema.Implementation.builder(
                        "careerforge-test-mcp",
                        "0.0.1"
                ).build()
        ).build();
    }

    private McpSchema.CallToolResult successCallResult() {
        return McpSchema.CallToolResult.builder()
                .addTextContent(
                        """
                        {"status":"SUCCESS","data":{"status":"NO_EVIDENCE"},"error":null}
                        """
                )
                .isError(false)
                .build();
    }

    private ProtocolCaseResult runCase(
            String caseId,
            String dimension,
            ProtocolAssertion assertion
    ) {
        try {
            assertion.verify();
            return new ProtocolCaseResult(caseId, dimension, true, null);
        } catch (Throwable throwable) {
            return new ProtocolCaseResult(
                    caseId,
                    dimension,
                    false,
                    throwable.getClass().getSimpleName()
            );
        }
    }

    private void printReport(List<ProtocolCaseResult> results) {
        System.out.println();
        System.out.println("================ MCP Protocol Evaluation ================");

        results.forEach(result -> System.out.printf(
                Locale.ROOT,
                "%s | %-24s | %s%s%n",
                result.caseId(),
                result.dimension(),
                result.passed() ? "PASS" : "FAIL",
                result.failureType() == null ? "" : " | failure=" + result.failureType()
        ));

        long passed = results.stream()
                .filter(ProtocolCaseResult::passed)
                .count();

        System.out.println("---------------------------------------------------------");
        System.out.println("Execution Boundary: MOCKED_SDK_DELEGATE");
        System.out.println("Distinct Cases: " + results.size());
        System.out.printf(
                Locale.ROOT,
                "Protocol Success Rate: %d/%d (%.2f%%)%n",
                passed,
                results.size(),
                passed * 100.0 / results.size()
        );
        System.out.println("External MCP Processes: 0");
        System.out.println("External Model Calls: 0");
        System.out.println("=========================================================");
    }

    @FunctionalInterface
    private interface ProtocolAssertion {
        void verify();
    }

    private record ProtocolCaseResult(
            String caseId,
            String dimension,
            boolean passed,
            String failureType
    ) {
    }
}