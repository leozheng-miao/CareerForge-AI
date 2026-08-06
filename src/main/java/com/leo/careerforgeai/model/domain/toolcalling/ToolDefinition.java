package com.leo.careerforgeai.model.domain.toolcalling;

import java.util.regex.Pattern;

/** 以供应商无关的方式描述一个模型可见工具。 */
public record ToolDefinition(String name, String description, String inputSchemaJson) {

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public ToolDefinition {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("工具名称必须由字母、数字、下划线或短横线组成，长度为 1 到 64");
        }
        if (description == null || description.isBlank()) throw new IllegalArgumentException("工具描述不能为空");
        if (inputSchemaJson == null || inputSchemaJson.isBlank()) throw new IllegalArgumentException("工具输入 Schema 不能为空");
    }
}