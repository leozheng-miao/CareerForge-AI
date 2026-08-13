package com.leo.careerforgeai.agent.api.dto;

import com.leo.careerforgeai.memory.domain.conversation.CoachingSession;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSessionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回当前用户拥有的会话元数据和乐观锁版本
 * @author: Miao Zheng
 * @date: 2026-08-13
 **/
public record CoachingSessionResponse(
        UUID sessionId,
        String title,
        CoachingSessionStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {

    public static CoachingSessionResponse from(CoachingSession session) {
        return new CoachingSessionResponse(
                session.sessionId(),
                session.title(),
                session.status(),
                session.version(),
                session.createdAt(),
                session.updatedAt(),
                session.closedAt()
        );
    }
}