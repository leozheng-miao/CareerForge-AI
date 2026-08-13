package com.leo.careerforgeai.agent.api.dto;

import com.leo.careerforgeai.memory.domain.conversation.CoachingSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @program: CareerForge-AI
 * @description: 定义创建Career Coach会话时允许客户端提交的标题
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
public record CreateCoachingSessionRequest(
        @NotBlank
        @Size(max = CoachingSession.MAX_TITLE_LENGTH)
        String title
) {
}