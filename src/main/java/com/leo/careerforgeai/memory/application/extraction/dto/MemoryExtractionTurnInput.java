package com.leo.careerforgeai.memory.application.extraction.dto;

import com.leo.careerforgeai.memory.domain.conversation.ConversationTurnRole;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义发送给Memory提取模型的最小Conversation Turn数据
 * @author: Miao Zheng
 * @date: 2026-08-13
 * @param turnId 可供模型引用的Turn白名单ID
 * @param role Turn的用户或助手角色
 * @param content 已持久化并通过领域校验的Turn正文
 **/
public record MemoryExtractionTurnInput(
        UUID turnId,
        ConversationTurnRole role,
        String content
) {
}