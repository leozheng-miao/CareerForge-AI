package com.leo.careerforgeai.memory.application.port.conversation;

import com.leo.careerforgeai.memory.domain.conversation.CoachingSession;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSessionStatus;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供当前用户Coaching Session稳定分页查询
 * @author: Miao Zheng
 * @date: 2026-09-03
 */
public interface CoachingSessionQueryRepository {

    List<CoachingSession> findSessionPage(
            ActorId ownerId,
            CoachingSessionStatus status,
            Instant beforeUpdatedAt,
            UUID beforeSessionId,
            int limit
    );

    List<ConversationTurn> findTurnPage(
            ActorId ownerId,
            UUID sessionId,
            Long beforeTurnSequence,
            int limit
    );
}