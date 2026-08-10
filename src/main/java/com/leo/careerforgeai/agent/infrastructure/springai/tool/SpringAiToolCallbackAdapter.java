package com.leo.careerforgeai.agent.infrastructure.springai.tool;

import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.domain.tool.ToolContract;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionErrorType;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCall;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 将公共Tool Contract和安全执行器适配为Spring AI ToolCallback。
 * @author: Miao Zheng
 * @date: 2026-08-07 20:00
 **/
public final class SpringAiToolCallbackAdapter implements ToolCallback {

    private static final String INVALID_ARGUMENTS_RESULT =
            "{\"status\":\"FAILURE\",\"data\":null,\"error\":{\"type\":\"INVALID_ARGUMENTS\",\"message\":\"工具参数不合法\"}}";

    private final String toolName;
    private final org.springframework.ai.tool.definition.ToolDefinition toolDefinition;
    private final SafeToolExecutor safeToolExecutor;

    public SpringAiToolCallbackAdapter(ToolContract<?, ?> contract,
                                       SpringAiToolDefinitionAdapter definitionAdapter,
                                       SafeToolExecutor safeToolExecutor) {
        Objects.requireNonNull(contract, "contract不能为空");
        this.toolName = contract.name();
        this.toolDefinition = Objects.requireNonNull(definitionAdapter, "definitionAdapter不能为空").adapt(contract);
        this.safeToolExecutor = Objects.requireNonNull(safeToolExecutor, "safeToolExecutor不能为空");
    }

    @Override
    public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    /** 禁止脱离服务端ToolContext直接执行工具。 */
    @Override
    public String call(String toolInput) {
        throw new IllegalStateException("Spring AI工具调用缺少服务端运行上下文");
    }

    /** 使用请求级可信上下文调用现有安全执行器并收集执行结果。 */
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        //1. 读取可信 SpringAiToolRunContext
        SpringAiToolRunContext runContext = SpringAiToolRunContext.requireFrom(toolContext);
        String localToolCallId = runContext.nextLocalToolCallId();
        ToolExecutionResult result;

        try {
            //2. 构建 ToolCall
            ToolCall toolCall = new ToolCall(localToolCallId, toolName, toolInput);
            //3. 通过 safeToolExecutor 执行
            result = safeToolExecutor.execute(toolCall, runContext.executionContext());
        } catch (IllegalArgumentException exception) {
            result = ToolExecutionResult.failure(localToolCallId, toolName,
                    INVALID_ARGUMENTS_RESULT, ToolExecutionErrorType.INVALID_ARGUMENTS);
        }

        runContext.record(result);
        return result.resultJson();
    }
}