package com.leo.careerforgeai.model.domain.toolcalling;

import com.leo.careerforgeai.model.domain.ModelRole;

/** 表示由服务端构造的 System 或 User 文本消息。 */
public record ToolCallingTextMessage(ModelRole role, String content) implements ToolCallingMessage {

    public ToolCallingTextMessage {
        if (role != ModelRole.SYSTEM && role != ModelRole.USER) {
            throw new IllegalArgumentException("Tool Calling 文本消息只允许 SYSTEM 或 USER");
        }
        if (content == null || content.isBlank()) throw new IllegalArgumentException("消息内容不能为空");
    }
}