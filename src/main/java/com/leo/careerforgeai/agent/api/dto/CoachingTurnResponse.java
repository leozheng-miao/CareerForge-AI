package com.leo.careerforgeai.agent.api.dto;

import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurnRole;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurnStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回当前用户会话中的可展示Turn及其Memory提取资格
 * @author: Miao Zheng
 * @date: 2026-08-13
 * @param turnId 服务端生成的Turn标识
 * @param turnSequence 会话内消息顺序
 * @param role 消息角色
 * @param status 消息完成状态
 * @param content 已完成消息正文，失败记录为空
 * @param memoryExtractionEligible 当前Turn是否允许参与Memory候选提取
 * @param createdAt 服务端记录时间
 **/
public record CoachingTurnResponse(
        UUID turnId,
        long turnSequence,
        ConversationTurnRole role,
        ConversationTurnStatus status,
        String content,
        boolean memoryExtractionEligible,
        Instant createdAt
) {

    public static CoachingTurnResponse from(ConversationTurn turn) {
        return new CoachingTurnResponse(
                turn.turnId(),
                turn.turnSequence(),
                turn.role(),
                turn.status(),
                turn.content(),
                turn.isEligibleForMemoryExtraction(),
                turn.createdAt()
        );
    }
}