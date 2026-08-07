package com.leo.careerforgeai.model.domain.toolcalling;

/**
 * @program: CareerForge-AI
 * @description: 表示模型产生的一次具有协议级长度边界但尚未经过业务校验的工具调用。
 * @author: Miao Zheng
 * @date: 2026-08-07 05:10
 **/
public record ToolCall(String id, String name, String argumentsJson) {

    private static final int MAX_ID_CHARS = 128;
    private static final int MAX_NAME_CHARS = 64;
    private static final int MAX_ARGUMENTS_CHARS = 30_000;

    public ToolCall {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Tool Call ID不能为空");
        if (id.length() > MAX_ID_CHARS) throw new IllegalArgumentException("Tool Call ID超过长度限制");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("工具名称不能为空");
        if (name.length() > MAX_NAME_CHARS) throw new IllegalArgumentException("工具名称超过长度限制");
        if (argumentsJson == null || argumentsJson.isBlank()) throw new IllegalArgumentException("工具参数不能为空");
        if (argumentsJson.length() > MAX_ARGUMENTS_CHARS) throw new IllegalArgumentException("工具参数超过协议级长度限制");
    }
}