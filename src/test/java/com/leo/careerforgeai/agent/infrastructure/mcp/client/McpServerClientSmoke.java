
package com.leo.careerforgeai.agent.infrastructure.mcp.client;

import com.leo.careerforgeai.CareerForgeAiApplication;
import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.application.tool.career.parse.SearchCareerMaterialsTool;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.knowledge.application.evidence.KnowledgeEvidenceSearchService;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static com.leo.careerforgeai.agent.infrastructure.mcp.client.McpInteropClientException.ErrorType.TOOL_CALL_FAILED;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @program: CareerForge-AI
 * @description: 通过真实Streamable HTTP验证MCP初始化、能力协商、工具发现和工具调用。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
@SpringBootTest(
        classes = CareerForgeAiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.ai.model.chat=none",
                "server.address=127.0.0.1",
                "careerforge.model.base-url=http://localhost",
                "careerforge.model.api-key=test-placeholder",
                "careerforge.model.name=test-model"
        }
)
@ActiveProfiles("mcp-smoke")
class McpServerClientSmoke {

    private static final String SUCCESS_RESULT_JSON = """
            {
              "status": "SUCCESS",
              "data": {
                "status": "NO_EVIDENCE",
                "requestId": "mcp-smoke-request",
                "evidence": [],
                "usedContentChars": 0,
                "candidateCount": 0,
                "errorType": null
              },
              "error": null
            }
            """;

    @LocalServerPort
    private int serverPort;

    @MockitoBean
    private SafeToolExecutor safeToolExecutor;

    @MockitoBean
    private KnowledgeEvidenceSearchService evidenceSearchService;

    @Test
    @DisplayName("独立Client通过真实MCP协议发现并调用唯一职业材料工具")
    void shouldInitializeDiscoverAndCallSearchTool() {
        when(safeToolExecutor.execute(any(), any())).thenReturn(
                ToolExecutionResult.success(
                        "mcp-smoke-call",
                        SearchCareerMaterialsTool.NAME,
                        SUCCESS_RESULT_JSON,
                        0,
                        null,
                        null
                )
        );

        URI serverUri = URI.create("http://127.0.0.1:" + serverPort);

        try (McpInteropClient client = McpInteropClientFactory.create(
                McpInteropClientSettings.local(serverUri)
        )) {
            McpSchema.InitializeResult initialization = client.initialize();

            assertThat(initialization.protocolVersion()).isNotBlank();
            assertThat(initialization.serverInfo().name())
                    .isEqualTo("careerforge-career-materials-mcp");
            assertThat(initialization.serverInfo().version()).isEqualTo("0.0.1");

            McpSchema.ServerCapabilities capabilities = initialization.capabilities();
            assertThat(capabilities.tools()).isNotNull();
            assertThat(capabilities.resources()).isNull();
            assertThat(capabilities.prompts()).isNull();
            assertThat(capabilities.completions()).isNull();

            McpSchema.ListToolsResult toolsResult = client.listTools();

            assertThat(toolsResult.tools())
                    .extracting(McpSchema.Tool::name)
                    .containsExactly(SearchCareerMaterialsTool.NAME);

            McpSchema.Tool searchTool = toolsResult.tools().getFirst();
            assertThat(searchTool.annotations().readOnlyHint()).isTrue();
            assertThat(searchTool.annotations().destructiveHint()).isFalse();

            McpSchema.CallToolResult callResult = client.callTool(
                    SearchCareerMaterialsTool.NAME,
                    Map.of("query", "Java Agent需要掌握哪些职业技能")
            );

            assertThat(callResult.isError()).isFalse();
            assertThat(callResult.structuredContent()).isInstanceOf(Map.class);

            Map<?, ?> structuredContent =
                    (Map<?, ?>) callResult.structuredContent();

            assertThat(structuredContent.get("status")).isEqualTo("NO_EVIDENCE");
            assertThat(structuredContent.get("requestId"))
                    .isEqualTo("mcp-smoke-request");

            McpSchema.CallToolResult invalidArgumentsResult = client.callTool(
                    SearchCareerMaterialsTool.NAME,
                    Map.of("query", "")
            );

            assertThat(invalidArgumentsResult.isError()).isTrue();
            assertThat(invalidArgumentsResult.structuredContent()).isNull();

            assertThatThrownBy(() ->
                    client.callTool(
                            "unknown_tool",
                            Map.of("query", "Java")
                    )
            ).isInstanceOfSatisfying(McpInteropClientException.class, exception -> {
                assertThat(exception.errorType()).isEqualTo(TOOL_CALL_FAILED);
                assertThat(exception.getMessage())
                        .isEqualTo("MCP工具调用失败")
                        .doesNotContain(
                                "Unknown tool",
                                "unknown_tool",
                                "Tool not found"
                        );
            });

            verify(safeToolExecutor, times(1)).execute(any(), any());

            System.out.printf(
                    "protocolVersion=%s%nserver=%s:%s%ntoolCapability=%s%nresources=%s%nprompts=%s%ncompletions=%s%ntools=%s%ncallIsError=%s%ninvalidArgumentsRejected=%s%nunknownToolRejected=true%nstructuredContent=%s%n",
                    initialization.protocolVersion(),
                    initialization.serverInfo().name(),
                    initialization.serverInfo().version(),
                    capabilities.tools() != null,
                    capabilities.resources() != null,
                    capabilities.prompts() != null,
                    capabilities.completions() != null,
                    toolsResult.tools().stream().map(McpSchema.Tool::name).toList(),
                    callResult.isError(),
                    invalidArgumentsResult.isError(),
                    structuredContent
            );
        }
    }
}