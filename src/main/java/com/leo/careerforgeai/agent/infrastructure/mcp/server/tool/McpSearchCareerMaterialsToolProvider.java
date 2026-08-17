package com.leo.careerforgeai.agent.infrastructure.mcp.server.tool;

import com.leo.careerforgeai.agent.application.coach.CareerCoachScopeProvider;
import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.application.tool.career.search.SearchCareerMaterialsTool;
import com.leo.careerforgeai.agent.domain.tool.ToolContract;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
import com.leo.careerforgeai.agent.domain.tool.career.search.SearchCareerMaterialsInput;
import com.leo.careerforgeai.agent.domain.tool.career.search.SearchCareerMaterialsOutput;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 将职业材料搜索公共契约和安全执行边界适配为唯一的MCP Tool规范。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
@Slf4j
public final class McpSearchCareerMaterialsToolProvider {

    private static final String SAFE_ADAPTER_FAILURE_JSON =
            "{\"status\":\"FAILURE\",\"data\":null,\"error\":{\"type\":\"EXECUTION_FAILED\",\"message\":\"MCP工具执行失败\"}}";

    private final ToolContract<SearchCareerMaterialsInput, SearchCareerMaterialsOutput> contract;
    private final SafeToolExecutor safeToolExecutor;
    private final CareerCoachScopeProvider scopeProvider;
    private final Clock clock;
    private final JsonMapper jsonMapper;
    private final McpJsonMapper mcpJsonMapper;
    private final List<McpServerFeatures.SyncToolSpecification> specifications;

    public McpSearchCareerMaterialsToolProvider(SearchCareerMaterialsTool searchTool,
                                                SafeToolExecutor safeToolExecutor,
                                                CareerCoachScopeProvider scopeProvider,
                                                Clock clock,
                                                JsonMapper jsonMapper) {
        this.contract = Objects.requireNonNull(searchTool, "searchTool不能为空").contract();
        this.safeToolExecutor = Objects.requireNonNull(safeToolExecutor, "safeToolExecutor不能为空");
        this.scopeProvider = Objects.requireNonNull(scopeProvider, "scopeProvider不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper不能为空");
        this.mcpJsonMapper = new JacksonMcpJsonMapper(jsonMapper);

        if (!contract.readOnly()) throw new IllegalArgumentException("MCP只允许暴露只读职业材料工具");

        McpSchema.Tool mcpTool = McpSchema.Tool
                .builder(contract.name(), mcpJsonMapper, contract.definition().inputSchemaJson())
                .description(contract.definition().description())
                .outputSchema(mcpJsonMapper, contract.outputSchemaJson())
                .annotations(McpSchema.ToolAnnotations.builder()
                        .readOnlyHint(true)
                        .destructiveHint(false)
                        .build())
                .build();

        this.specifications = List.of(
                new McpServerFeatures.SyncToolSpecification(mcpTool, this::call)
        );
    }

    /** 返回只包含search_career_materials的不可变MCP工具规范。 */
    public List<McpServerFeatures.SyncToolSpecification> specifications() {
        return specifications;
    }

    /** 将MCP调用转换为受服务端Scope和Deadline约束的安全Java工具调用。 */
    private McpSchema.CallToolResult call(McpSyncServerExchange ignoredExchange,
                                          McpSchema.CallToolRequest request) {
        String runId = "mcp-run-" + UUID.randomUUID();
        String toolCallId = "mcp-call-" + UUID.randomUUID();

        try {
            if (request == null || !contract.name().equals(request.name())) {
                return adapterFailure();
            }

            String argumentsJson = jsonMapper.writeValueAsString(request.arguments());
            Instant startedAt = clock.instant();
            ToolExecutionContext context = new ToolExecutionContext(
                    runId,
                    startedAt.plus(contract.timeout()),
                    scopeProvider.scope()
            );
            ToolExecutionResult result = safeToolExecutor.execute(
                    new ToolCall(toolCallId, contract.name(), argumentsJson),
                    context
            );

            return toMcpResult(result);
        } catch (Exception exception) {
            log.warn("MCP工具适配失败，runId={}, exceptionType={}",
                    runId, exception.getClass().getSimpleName());
            return adapterFailure();
        }
    }

    /** 将安全执行结果映射为MCP文本结果、结构化结果和错误标记。 */
    private McpSchema.CallToolResult toMcpResult(ToolExecutionResult result) throws Exception {
        McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                .addTextContent(result.resultJson())
                .isError(result.status() == ToolExecutionStatus.FAILURE);

        if (result.status() == ToolExecutionStatus.SUCCESS) {
            JsonNode envelope = jsonMapper.readTree(result.resultJson());
            JsonNode data = envelope == null ? null : envelope.get("data");
            if (data == null || !data.isObject()) return adapterFailure();

            builder.structuredContent(
                    mcpJsonMapper,
                    jsonMapper.writeValueAsString(data)
            );
        }

        return builder.build();
    }

    /** 返回不包含异常消息、堆栈、配置或内部路径的固定MCP失败结果。 */
    private McpSchema.CallToolResult adapterFailure() {
        return McpSchema.CallToolResult.builder()
                .addTextContent(SAFE_ADAPTER_FAILURE_JSON)
                .isError(true)
                .build();
    }
}