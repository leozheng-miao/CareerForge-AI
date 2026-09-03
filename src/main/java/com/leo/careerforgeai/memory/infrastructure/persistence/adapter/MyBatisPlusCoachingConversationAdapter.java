package com.leo.careerforgeai.memory.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.careerforgeai.memory.application.port.conversation.CoachingConversationRepository;
import com.leo.careerforgeai.memory.application.port.conversation.CoachingSessionQueryRepository;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSession;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSessionStatus;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.infrastructure.persistence.converter.CoachingConversationPersistenceConverter;
import com.leo.careerforgeai.memory.infrastructure.persistence.entity.CoachingSessionEntity;
import com.leo.careerforgeai.memory.infrastructure.persistence.entity.ConversationTurnEntity;
import com.leo.careerforgeai.memory.infrastructure.persistence.mapper.CoachingSessionMapper;
import com.leo.careerforgeai.memory.infrastructure.persistence.mapper.ConversationTurnMapper;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus实现Coaching Session和Conversation Turn持久化端口
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Repository
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MyBatisPlusCoachingConversationAdapter implements CoachingConversationRepository, CoachingSessionQueryRepository {
    public static final int MAX_RECENT_TURNS = 100;

    private final CoachingSessionMapper sessionMapper;
    private final ConversationTurnMapper turnMapper;
    private final CoachingConversationPersistenceConverter converter;

    public MyBatisPlusCoachingConversationAdapter(
            CoachingSessionMapper sessionMapper,
            ConversationTurnMapper turnMapper,
            CoachingConversationPersistenceConverter converter
    ) {
        this.sessionMapper = Objects.requireNonNull(sessionMapper, "sessionMapper 不能为空");
        this.turnMapper = Objects.requireNonNull(turnMapper, "turnMapper 不能为空");
        this.converter = Objects.requireNonNull(converter, "converter 不能为空");
    }

    @Override
    public void insertSession(CoachingSession session) {
        int affectedRows = sessionMapper.insert(converter.toEntity(session));
        requireSingleAffectedRow(affectedRows, "插入Coaching Session失败");
    }

    @Override
    public Optional<CoachingSession> findSession(ActorId ownerId, UUID sessionId) {
        requireOwnerAndId(ownerId, sessionId, "sessionId");

        LambdaQueryWrapper<CoachingSessionEntity> query = new LambdaQueryWrapper<>();
        query.eq(CoachingSessionEntity::getOwnerId, ownerId.value())
                .eq(CoachingSessionEntity::getSessionId, sessionId.toString());

        return Optional.ofNullable(sessionMapper.selectOne(query)).map(converter::toDomain);
    }

    @Override
    public boolean updateSessionIfVersionMatches(
            ActorId ownerId,
            CoachingSession updatedSession,
            long expectedVersion
    ) {
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(updatedSession, "updatedSession 不能为空");

        if (!ownerId.equals(updatedSession.ownerId())) {
            throw new IllegalArgumentException("ownerId与Session归属不一致");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion不能小于0");
        }
        if (updatedSession.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("更新后的version必须比旧version增加1");
        }

        int affectedRows = sessionMapper.updateIfVersionMatches(
                updatedSession.sessionId().toString(),
                ownerId.value(),
                updatedSession.status().name(),
                updatedSession.nextTurnSequence(),
                updatedSession.version(),
                updatedSession.updatedAt(),
                updatedSession.closedAt(),
                expectedVersion
        );

        if (affectedRows > 1) {
            throw new IllegalStateException("Session乐观锁更新影响了多行数据");
        }

        return affectedRows == 1;
    }

    @Override
    public void insertTurn(ConversationTurn turn) {
        int affectedRows = turnMapper.insert(converter.toEntity(turn));
        requireSingleAffectedRow(affectedRows, "插入Conversation Turn失败");
    }

    @Override
    public Optional<ConversationTurn> findTurn(ActorId ownerId, UUID turnId) {
        requireOwnerAndId(ownerId, turnId, "turnId");

        LambdaQueryWrapper<ConversationTurnEntity> query = new LambdaQueryWrapper<>();
        query.eq(ConversationTurnEntity::getOwnerId, ownerId.value())
                .eq(ConversationTurnEntity::getTurnId, turnId.toString());

        return Optional.ofNullable(turnMapper.selectOne(query)).map(converter::toDomain);
    }

    @Override
    public List<ConversationTurn> findRecentTurns(ActorId ownerId, UUID sessionId, int limit) {
        requireOwnerAndId(ownerId, sessionId, "sessionId");

        if (limit < 1 || limit > MAX_RECENT_TURNS) {
            throw new IllegalArgumentException("limit必须在1到" + MAX_RECENT_TURNS + "之间");
        }

        return turnMapper.selectRecentTurns(ownerId.value(), sessionId.toString(), limit)
                .stream()
                .map(converter::toDomain)
                .toList();
    }

    private static void requireOwnerAndId(ActorId ownerId, UUID id, String fieldName) {
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(id, fieldName + " 不能为空");
    }

    private static void requireSingleAffectedRow(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new IllegalStateException(message + ": affectedRows=" + affectedRows);
        }
    }

    @Override
    public List<CoachingSession> findSessionPage(ActorId ownerId, CoachingSessionStatus status,
                                                 Instant beforeUpdatedAt, UUID beforeSessionId, int limit) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        if ((beforeUpdatedAt == null) != (beforeSessionId == null)) {
            throw new IllegalArgumentException("分页位置必须同时包含updatedAt和sessionId");
        }
        if (limit < 1 || limit > 51) throw new IllegalArgumentException("limit必须在1到51之间");

        LambdaQueryWrapper<CoachingSessionEntity> query = new LambdaQueryWrapper<>();
        query.eq(CoachingSessionEntity::getOwnerId, ownerId.value());
        if (status != null) query.eq(CoachingSessionEntity::getSessionStatus, status.name());
        if (beforeUpdatedAt != null) {
            query.and(position -> position
                    .lt(CoachingSessionEntity::getUpdatedAt, beforeUpdatedAt)
                    .or(sameTime -> sameTime
                            .eq(CoachingSessionEntity::getUpdatedAt, beforeUpdatedAt)
                            .lt(CoachingSessionEntity::getSessionId, beforeSessionId.toString())));
        }
        query.orderByDesc(CoachingSessionEntity::getUpdatedAt)
                .orderByDesc(CoachingSessionEntity::getSessionId)
                .last("LIMIT " + limit);
        return sessionMapper.selectList(query).stream().map(converter::toDomain).toList();
    }

    @Override
    public List<ConversationTurn> findTurnPage(
            ActorId ownerId,
            UUID sessionId,
            Long beforeTurnSequence,
            int limit
    ) {
        requireOwnerAndId(ownerId, sessionId, "sessionId");
        if (beforeTurnSequence != null && beforeTurnSequence < 1) {
            throw new IllegalArgumentException("beforeTurnSequence必须大于0");
        }
        if (limit < 1 || limit > 51) throw new IllegalArgumentException("limit必须在1到51之间");

        LambdaQueryWrapper<ConversationTurnEntity> query = new LambdaQueryWrapper<>();
        query.eq(ConversationTurnEntity::getOwnerId, ownerId.value())
                .eq(ConversationTurnEntity::getSessionId, sessionId.toString());
        if (beforeTurnSequence != null) {
            query.lt(ConversationTurnEntity::getTurnSequence, beforeTurnSequence);
        }
        query.orderByDesc(ConversationTurnEntity::getTurnSequence).last("LIMIT " + limit);
        return turnMapper.selectList(query).stream().map(converter::toDomain).toList();
    }
}