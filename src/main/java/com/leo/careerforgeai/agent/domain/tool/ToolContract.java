package com.leo.careerforgeai.agent.domain.tool;

import com.leo.careerforgeai.model.domain.toolcalling.ToolDefinition;

import java.time.Duration;
import java.util.Objects;

/** 作为原生 DeepSeek、Spring AI 和 MCP 适配器共同使用的工具事实源。 */
public record ToolContract<I, O>(
        ToolDefinition definition,
        String outputSchemaJson,
        Class<I> inputType,
        Class<O> outputType,
        ToolImplementationType implementationType,
        ToolRiskLevel riskLevel,
        boolean readOnly,
        int maxArgumentsChars,
        int maxResultChars,
        int maxResultItems,
        Duration timeout
) {

    public ToolContract {
        Objects.requireNonNull(definition, "definition 不能为空");
        if (outputSchemaJson == null || outputSchemaJson.isBlank()) {
            throw new IllegalArgumentException("outputSchemaJson 不能为空");
        }
        Objects.requireNonNull(inputType, "inputType 不能为空");
        Objects.requireNonNull(outputType, "outputType 不能为空");
        Objects.requireNonNull(implementationType, "implementationType 不能为空");
        Objects.requireNonNull(riskLevel, "riskLevel 不能为空");
        if (maxArgumentsChars <= 0) throw new IllegalArgumentException("maxArgumentsChars 必须大于 0");
        if (maxResultChars <= 0) throw new IllegalArgumentException("maxResultChars 必须大于 0");
        if (maxResultItems <= 0) throw new IllegalArgumentException("maxResultItems 必须大于 0");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout 必须大于 0");
        }
    }

    public String name() {
        return definition.name();
    }
}