package com.leo.careerforgeai.memory.infrastructure.persistence.converter;

import com.leo.careerforgeai.memory.domain.conversation.CoachingSession;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSessionStatus;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurnRole;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurnStatus;
import com.leo.careerforgeai.memory.infrastructure.persistence.entity.CoachingSessionEntity;
import com.leo.careerforgeai.memory.infrastructure.persistence.entity.ConversationTurnEntity;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 在会话领域对象和MyBatis-Plus数据库Entity之间执行受控转换
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Component
public class CoachingConversationPersistenceConverter {

    /** 将会话领域对象转换为数据库Entity。 */
    public CoachingSessionEntity toEntity(CoachingSession session) {
        Objects.requireNonNull(session, "session 不能为空");

        CoachingSessionEntity entity = new CoachingSessionEntity();
        entity.setSessionId(session.sessionId().toString());
        entity.setOwnerId(session.ownerId().value());
        entity.setTitle(session.title());
        entity.setSessionStatus(session.status().name());
        entity.setNextTurnSequence(session.nextTurnSequence());
        entity.setVersion(session.version());
        entity.setCreatedAt(session.createdAt());
        entity.setUpdatedAt(session.updatedAt());
        entity.setClosedAt(session.closedAt());

        return entity;
    }

    /** 将数据库会话Entity还原为领域对象并重新执行领域校验。 */
    public CoachingSession toDomain(CoachingSessionEntity entity) {
        Objects.requireNonNull(entity, "entity 不能为空");

        return new CoachingSession(
                UUID.fromString(entity.getSessionId()),
                new ActorId(entity.getOwnerId()),
                entity.getTitle(),
                CoachingSessionStatus.valueOf(entity.getSessionStatus()),
                requireLong(entity.getNextTurnSequence(), "nextTurnSequence"),
                requireLong(entity.getVersion(), "version"),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getClosedAt()
        );
    }

    /** 将Conversation Turn领域对象转换为数据库Entity。 */
    public ConversationTurnEntity toEntity(ConversationTurn turn) {
        Objects.requireNonNull(turn, "turn 不能为空");

        ConversationTurnEntity entity = new ConversationTurnEntity();
        entity.setTurnId(turn.turnId().toString());
        entity.setSessionId(turn.sessionId().toString());
        entity.setExchangeId(turn.exchangeId().toString());
        entity.setOwnerId(turn.ownerId().value());
        entity.setTurnSequence(turn.turnSequence());
        entity.setTurnRole(turn.role().name());
        entity.setTurnStatus(turn.status().name());
        entity.setContent(turn.content());
        entity.setContentHash(turn.contentHash());
        entity.setAgentRunId(turn.agentRunId());
        entity.setFailureCode(turn.failureCode());
        entity.setCreatedAt(turn.createdAt());

        return entity;
    }

    /** 将数据库Turn Entity还原为领域对象并拒绝非法状态组合。 */
    public ConversationTurn toDomain(ConversationTurnEntity entity) {
        Objects.requireNonNull(entity, "entity 不能为空");

        return new ConversationTurn(
                UUID.fromString(entity.getTurnId()),
                UUID.fromString(entity.getSessionId()),
                UUID.fromString(entity.getExchangeId()),
                new ActorId(entity.getOwnerId()),
                requireLong(entity.getTurnSequence(), "turnSequence"),
                ConversationTurnRole.valueOf(entity.getTurnRole()),
                ConversationTurnStatus.valueOf(entity.getTurnStatus()),
                entity.getContent(),
                entity.getContentHash(),
                entity.getAgentRunId(),
                entity.getFailureCode(),
                entity.getCreatedAt()
        );
    }

    private static long requireLong(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("数据库" + fieldName + "不能为空");
        }

        return value;
    }
}