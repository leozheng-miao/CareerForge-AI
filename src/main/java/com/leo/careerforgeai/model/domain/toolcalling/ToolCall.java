package com.leo.careerforgeai.model.domain.toolcalling;

/** 表示模型产生的一次未经业务信任的工具调用请求。 */
public record ToolCall(String id, String name, String argumentsJson) {

    public ToolCall {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Tool Call ID 不能为空");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("工具名称不能为空");
        if (argumentsJson == null || argumentsJson.isBlank()) throw new IllegalArgumentException("工具参数不能为空");
    }
}