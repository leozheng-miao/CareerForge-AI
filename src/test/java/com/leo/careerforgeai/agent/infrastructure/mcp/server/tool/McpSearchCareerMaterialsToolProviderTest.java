package com.leo.careerforgeai.agent.infrastructure.mcp.server.tool;

import com.leo.careerforgeai.agent.application.coach.CareerCoachScopeProvider;
import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.application.tool.career.search.SearchCareerMaterialsTool;
import com.leo.careerforgeai.agent.application.tool.career.search.CareerMaterialScopePolicy;
import com.leo.careerforgeai.agent.domain.tool.ToolContract;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionErrorType;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.agent.domain.tool.career.search.SearchCareerMaterialsInput;
import com.leo.careerforgeai.agent.domain.tool.career.search.SearchCareerMaterialsOutput;
import com.leo.careerforgeai.knowledge.application.evidence.KnowledgeEvidenceSearchService;
import com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证MCP职业材料Provider只暴露公共契约并复用服务端安全执行边界。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
class McpSearchCareerMaterialsToolProviderTest {

    private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");

    private JsonMapper jsonMapper;
    private SafeToolExecutor safeToolExecutor;
    private CareerCoachScopeProvider scopeProvider;
    private RetrievalScope scope;
    private ToolContract<SearchCareerMaterialsInput, SearchCareerMaterialsOutput> contract;
    private McpSearchCareerMaterialsToolProvider provider;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        safeToolExecutor = mock(SafeToolExecutor.class);
        scopeProvider = mock(CareerCoachScopeProvider.class);
        scope = new RetrievalScope(
                "careerforge-career-materials",
                Set.of(
                        KnowledgeDocumentType.JOB_DESCRIPTION,
                        KnowledgeDocumentType.INTERVIEW_EXPERIENCE
                ),
                Set.of("ai-job-jd-summary", "ai-interview-summary")
        );

        SearchCareerMaterialsTool searchTool = new SearchCareerMaterialsTool(
                mock(KnowledgeEvidenceSearchService.class),
                new CareerMaterialScopePolicy()
        );
        contract = searchTool.contract();
        when(scopeProvider.scope()).thenReturn(scope);

        provider = new McpSearchCareerMaterialsToolProvider(
                searchTool,
                safeToolExecutor,
                scopeProvider,
                Clock.fixed(NOW, ZoneOffset.UTC),
                jsonMapper
        );
    }

    @Test
    @DisplayName("只暴露一个与公共Contract语义一致的只读MCP工具")
    void shouldExposeOnlyPublicSearchContract() throws Exception {
        assertThat(provider.specifications()).hasSize(1);

        McpSchema.Tool tool = provider.specifications().getFirst().tool();

        assertThat(tool.name()).isEqualTo(contract.name());
        assertThat(tool.description()).isEqualTo(contract.definition().description());
        assertThat(tool.annotations().readOnlyHint()).isTrue();
        assertThat(tool.annotations().destructiveHint()).isFalse();

        assertThat(jsonMapper.readTree(jsonMapper.writeValueAsString(tool.inputSchema())))
                .isEqualTo(jsonMapper.readTree(contract.definition().inputSchemaJson()));
        assertThat(jsonMapper.readTree(jsonMapper.writeValueAsString(tool.outputSchema())))
                .isEqualTo(jsonMapper.readTree(contract.outputSchemaJson()));
    }

    @Test
    @DisplayName("成功调用复用服务端Scope、Contract超时和SafeToolExecutor")
    void shouldReuseServerScopeDeadlineAndSafeExecutor() throws Exception {
        String resultJson = """
                {
                  "status": "SUCCESS",
                  "data": {
                    "status": "NO_EVIDENCE",
                    "requestId": "request-1",
                    "evidence": [],
                    "usedContentChars": 0,
                    "candidateCount": 0,
                    "errorType": null
                  },
                  "error": null
                }
                """;

        when(safeToolExecutor.execute(any(), any())).thenReturn(
                ToolExecutionResult.success(
                        "mcp-call-test",
                        SearchCareerMaterialsTool.NAME,
                        resultJson,
                        0,
                        null,
                        null
                )
        );

        McpSchema.CallToolResult result = call(
                SearchCareerMaterialsTool.NAME,
                Map.of("query", "Java并发面试题")
        );

        assertThat(result.isError()).isFalse();
        assertThat(result.structuredContent()).isInstanceOf(Map.class);
        Map<?, ?> structuredContent = (Map<?, ?>) result.structuredContent();
        assertThat(structuredContent.get("status")).isEqualTo("NO_EVIDENCE");
        assertThat(structuredContent.get("requestId")).isEqualTo("request-1");

        McpSchema.TextContent textContent =
                (McpSchema.TextContent) result.content().getFirst();
        assertThat(jsonMapper.readTree(textContent.text()))
                .isEqualTo(jsonMapper.readTree(resultJson));

        ArgumentCaptor<ToolCall> toolCallCaptor =
                ArgumentCaptor.forClass(ToolCall.class);
        ArgumentCaptor<ToolExecutionContext> contextCaptor =
                ArgumentCaptor.forClass(ToolExecutionContext.class);

        verify(safeToolExecutor).execute(
                toolCallCaptor.capture(),
                contextCaptor.capture()
        );

        assertThat(toolCallCaptor.getValue().name())
                .isEqualTo(SearchCareerMaterialsTool.NAME);
        assertThat(jsonMapper.readTree(toolCallCaptor.getValue().argumentsJson()))
                .isEqualTo(jsonMapper.readTree("""
                        {"query":"Java并发面试题"}
                        """));

        assertThat(contextCaptor.getValue().retrievalScope()).isSameAs(scope);
        assertThat(contextCaptor.getValue().deadline())
                .isEqualTo(NOW.plus(contract.timeout()));
    }

    @Test
    @DisplayName("安全执行失败映射为MCP isError且不返回structuredContent")
    void shouldMapSafeExecutionFailureToMcpError() {
        String failureJson = """
                {
                  "status": "FAILURE",
                  "data": null,
                  "error": {
                    "type": "VALIDATION_FAILED",
                    "message": "工具参数不合法"
                  }
                }
                """;

        when(safeToolExecutor.execute(any(), any())).thenReturn(
                ToolExecutionResult.failure(
                        "mcp-call-test",
                        SearchCareerMaterialsTool.NAME,
                        failureJson,
                        ToolExecutionErrorType.VALIDATION_FAILED
                )
        );

        McpSchema.CallToolResult result = call(
                SearchCareerMaterialsTool.NAME,
                Map.of("query", "")
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.structuredContent()).isNull();

        McpSchema.TextContent textContent =
                (McpSchema.TextContent) result.content().getFirst();
        assertThat(textContent.text()).isEqualTo(failureJson);
    }

    @Test
    @DisplayName("适配层异常只返回固定脱敏错误")
    void shouldSanitizeUnexpectedAdapterFailure() {
        when(safeToolExecutor.execute(any(), any()))
                .thenThrow(new IllegalStateException(
                        "Authorization: Bearer secret-token /internal/path"
                ));

        McpSchema.CallToolResult result = call(
                SearchCareerMaterialsTool.NAME,
                Map.of("query", "Java")
        );

        assertThat(result.isError()).isTrue();
        assertThat(result.structuredContent()).isNull();

        McpSchema.TextContent textContent =
                (McpSchema.TextContent) result.content().getFirst();
        assertThat(textContent.text())
                .contains("MCP工具执行失败")
                .doesNotContain(
                        "secret-token",
                        "Authorization",
                        "/internal/path",
                        "IllegalStateException"
                );
    }

    @Test
    @DisplayName("Provider拒绝执行与公共Contract不一致的工具名")
    void shouldRejectMismatchedToolName() {
        McpSchema.CallToolResult result = call(
                "parse_job_requirements",
                Map.of("jobDescription", "test")
        );

        assertThat(result.isError()).isTrue();
        verifyNoInteractions(safeToolExecutor);
    }

    private McpSchema.CallToolResult call(
            String toolName,
            Map<String, Object> arguments
    ) {
        McpSchema.CallToolRequest request =
                new McpSchema.CallToolRequest(toolName, arguments);

        return provider.specifications()
                .getFirst()
                .callHandler()
                .apply(null, request);
    }
}