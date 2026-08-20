package com.leo.careerforgeai.agent.api.dto;

import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义同步提交Coaching Run所需的幂等身份、Session版本和消息
 * @author: Miao Zheng
 * @date: 2026-08-20
 * @param sessionId 当前用户拥有的Coaching Session
 * @param requestId 客户端为本次业务请求生成的幂等UUID
 * @param expectedSessionVersion 客户端预期的Session版本
 * @param message 当前用户消息
 **/
public record CreateCoachingRunRequest(
        @NotNull UUID sessionId,
        @NotNull UUID requestId,
        @NotNull @PositiveOrZero Long expectedSessionVersion,
        @NotBlank @Size(max = ConversationTurn.MAX_CONTENT_LENGTH) String message
) {
}