package com.leo.careerforgeai.agent.api.dto;

import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * @program: CareerForge-AI
 * @description: 定义会话消息接口允许客户端提交的版本前置条件和当前消息
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
public record SendCoachingMessageRequest(
        @NotNull
        @PositiveOrZero
        Long expectedSessionVersion,

        @NotBlank
        @Size(max = ConversationTurn.MAX_CONTENT_LENGTH)
        String message
) {
}