package com.leo.careerforgeai.model.domain.toolcalling;

import com.leo.careerforgeai.model.domain.ModelRole;

/**
 * @program: CareerForge-AI
 * @description: 表示Tool Calling请求中的SYSTEM、USER或ASSISTANT文本消息
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
public record ToolCallingTextMessage(ModelRole role, String content) implements ToolCallingMessage {

    public ToolCallingTextMessage {
        if (role == null) {
            throw new IllegalArgumentException("消息角色不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
    }
}