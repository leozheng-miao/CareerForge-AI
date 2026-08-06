package com.leo.careerforgeai.model.domain.toolcalling;

/** 控制模型是否可以、禁止或必须请求工具。 */
public enum ToolChoiceMode {
    AUTO,
    NONE,
    REQUIRED
}