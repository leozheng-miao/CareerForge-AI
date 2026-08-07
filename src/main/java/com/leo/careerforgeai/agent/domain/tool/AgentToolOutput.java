package com.leo.careerforgeai.agent.domain.tool;

import com.leo.careerforgeai.model.domain.ModelUsage;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 分离工具业务数据、执行状态、结果数量和内部模型Token。
 * @author: Miao Zheng
 * @date: 2026-08-06 21:20
 **/
public record AgentToolOutput<O>(
        O data,
        ToolExecutionStatus status,
        ToolExecutionErrorType errorType,
        Integer resultCount,
        ModelUsage modelUsage,
        Long modelDurationMs
) {

    public AgentToolOutput {
        Objects.requireNonNull(data, "data 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        if (resultCount != null && resultCount < 0) throw new IllegalArgumentException("resultCount 不能小于 0");

        if (status == ToolExecutionStatus.SUCCESS && errorType != null) {
            throw new IllegalArgumentException("成功工具输出不能包含 errorType");
        }
        if (status == ToolExecutionStatus.FAILURE && errorType == null) {
            throw new IllegalArgumentException("失败工具输出必须包含 errorType");
        }
        if (status == ToolExecutionStatus.FAILURE && resultCount != null) {
            throw new IllegalArgumentException("失败工具输出不能声明成功数量");
        }
        if (modelUsage != null && (modelUsage.inputTokens() < 0
                || modelUsage.outputTokens() < 0
                || modelUsage.totalTokens() < 0)) {
            throw new IllegalArgumentException("modelUsage 不能包含负数");
        }
        if (modelDurationMs != null && modelDurationMs < 0) {
            throw new IllegalArgumentException("modelDurationMs 不能小于 0");
        }
    }

    /** 创建不包含内部模型调用的成功工具输出。 */
    public static <O> AgentToolOutput<O> of(O data, Integer resultCount) {
        return new AgentToolOutput<>(data, ToolExecutionStatus.SUCCESS, null, resultCount, null, null);
    }

    /** 创建可能包含Rerank Token的检索型成功工具输出。 */
    public static <O> AgentToolOutput<O> retrievalBacked(O data, Integer resultCount, ModelUsage modelUsage) {
        return new AgentToolOutput<>(data, ToolExecutionStatus.SUCCESS, null, resultCount, modelUsage, null);
    }

    /** 创建包含内部模型Token和独立耗时的MODEL_BACKED成功输出。 */
    public static <O> AgentToolOutput<O> modelBacked(O data, Integer resultCount, ModelUsage modelUsage, long modelDurationMs) {
        return new AgentToolOutput<>(data, ToolExecutionStatus.SUCCESS, null, resultCount,
                Objects.requireNonNull(modelUsage, "modelUsage 不能为空"), modelDurationMs);
    }

    /** 创建不包含内部模型观测数据的安全业务失败输出。 */
    public static <O> AgentToolOutput<O> handledFailure(O data, ToolExecutionErrorType errorType) {
        return new AgentToolOutput<>(data, ToolExecutionStatus.FAILURE,
                Objects.requireNonNull(errorType, "errorType 不能为空"), null, null, null);
    }

    /** 创建保留已观测内部模型成本的MODEL_BACKED失败输出。 */
    public static <O> AgentToolOutput<O> modelBackedFailure(O data, ToolExecutionErrorType errorType,
                                                            ModelUsage modelUsage, long modelDurationMs) {
        return new AgentToolOutput<>(data, ToolExecutionStatus.FAILURE,
                Objects.requireNonNull(errorType, "errorType 不能为空"), null, modelUsage, modelDurationMs);
    }
}