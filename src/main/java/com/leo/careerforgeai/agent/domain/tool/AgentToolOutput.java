package com.leo.careerforgeai.agent.domain.tool;

import com.leo.careerforgeai.model.domain.ModelUsage;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 分离工具业务数据与仅供 Java Trace 使用的结果数量和内部模型 Token。
 * @author: Miao Zheng
 * @date: 2026-08-06 18:03
 **/
public record AgentToolOutput<O>(
        O data,
        Integer resultCount,
        ModelUsage modelUsage
) {

    public AgentToolOutput {
        Objects.requireNonNull(data, "data 不能为空");
        if (resultCount != null && resultCount < 0) throw new IllegalArgumentException("resultCount 不能小于 0");
        if (modelUsage != null && (modelUsage.inputTokens() < 0
                || modelUsage.outputTokens() < 0
                || modelUsage.totalTokens() < 0)) {
            throw new IllegalArgumentException("modelUsage 不能包含负数");
        }
    }

    /** 创建不包含内部模型调用的工具输出。 */
    public static <O> AgentToolOutput<O> of(O data, Integer resultCount) {
        return new AgentToolOutput<>(data, resultCount, null);
    }

    /** 创建包含内部模型 Token 的 MODEL_BACKED 工具输出。 */
    public static <O> AgentToolOutput<O> modelBacked(
            O data,
            Integer resultCount,
            ModelUsage modelUsage
    ) {
        return new AgentToolOutput<>(data, resultCount, Objects.requireNonNull(modelUsage, "modelUsage 不能为空"));
    }
}