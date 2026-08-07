package com.leo.careerforgeai.agent.domain.tool;

import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.domain.toolcalling.ToolResultMessage;

/**
 * @program: CareerForge-AI
 * @description: 保存可安全回传模型的结果 JSON 以及仅供 Java Trace 使用的执行元数据。
 * @author: Miao Zheng
 * @date: 2026-08-06 18:03
 **/
public record ToolExecutionResult(
        String toolCallId,
        String toolName,
        ToolExecutionStatus status,
        String resultJson,
        ToolExecutionErrorType errorType,
        Integer resultCount,
        ModelUsage modelUsage,
        Long modelDurationMs
) {

    public ToolExecutionResult {
        if (toolCallId == null || toolCallId.isBlank()) throw new IllegalArgumentException("toolCallId 不能为空");
        if (toolName == null || toolName.isBlank()) throw new IllegalArgumentException("toolName 不能为空");
        if (status == null) throw new IllegalArgumentException("status 不能为空");
        if (resultJson == null || resultJson.isBlank()) throw new IllegalArgumentException("resultJson 不能为空");
        if (resultCount != null && resultCount < 0) throw new IllegalArgumentException("resultCount 不能小于 0");

        if (status == ToolExecutionStatus.SUCCESS && errorType != null) {
            throw new IllegalArgumentException("成功结果不能包含 errorType");
        }
        if (status == ToolExecutionStatus.FAILURE && errorType == null) {
            throw new IllegalArgumentException("失败结果必须包含 errorType");
        }
        if (status == ToolExecutionStatus.FAILURE && resultCount != null) {
            throw new IllegalArgumentException("失败结果不能声明成功数量");
        }
        if (modelDurationMs != null && modelDurationMs < 0) {
            throw new IllegalArgumentException("modelDurationMs 不能小于 0");
        }
        if (modelUsage != null && (modelUsage.inputTokens() < 0
                || modelUsage.outputTokens() < 0
                || modelUsage.totalTokens() < 0)) {
            throw new IllegalArgumentException("modelUsage 不能包含负数");
        }
    }

    /** 创建工具成功结果。 */
    public static ToolExecutionResult success(String toolCallId, String toolName, String resultJson,
                                              Integer resultCount, ModelUsage modelUsage, Long modelDurationMs) {
        return new ToolExecutionResult(toolCallId, toolName, ToolExecutionStatus.SUCCESS,
                resultJson, null, resultCount, modelUsage, modelDurationMs);
    }

    /** 创建不包含业务输出元数据的工具失败结果。 */
    public static ToolExecutionResult failure(String toolCallId, String toolName, String resultJson,
                                              ToolExecutionErrorType errorType) {
        return failure(toolCallId, toolName, resultJson, errorType, null, null);
    }

    public static ToolExecutionResult failure(String toolCallId, String toolName, String resultJson,
                                              ToolExecutionErrorType errorType, ModelUsage modelUsage,
                                              Long modelDurationMs) {
        return new ToolExecutionResult(toolCallId, toolName, ToolExecutionStatus.FAILURE,
                resultJson, errorType, null, modelUsage, modelDurationMs);
    }

    /** 转换成下一轮模型请求需要的 Tool Result 消息。 */
    public ToolResultMessage toMessage() {
        return new ToolResultMessage(toolCallId, toolName, resultJson);
    }
}