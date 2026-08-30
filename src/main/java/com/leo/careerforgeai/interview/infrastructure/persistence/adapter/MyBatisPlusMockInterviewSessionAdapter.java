package com.leo.careerforgeai.interview.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.interview.infrastructure.persistence.converter.MockInterviewSessionPersistenceConverter;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.MockInterviewSessionEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.mapper.MockInterviewSessionMapper;
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
 * @description: 使用MyBatis-Plus实现模拟面试幂等认领、owner隔离查询和CAS更新
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Repository
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MyBatisPlusMockInterviewSessionAdapter implements MockInterviewSessionRepository {

    private final MockInterviewSessionMapper mapper;
    private final MockInterviewSessionPersistenceConverter converter;

    public MyBatisPlusMockInterviewSessionAdapter(
            MockInterviewSessionMapper mapper,
            MockInterviewSessionPersistenceConverter converter
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper不能为空");
        this.converter = Objects.requireNonNull(converter, "converter不能为空");
    }

    @Override
    public MockInterviewSession claim(MockInterviewSession candidate) {
        Objects.requireNonNull(candidate, "candidate不能为空");
        mapper.claim(converter.toEntity(candidate));
        return findByRequestId(candidate.ownerId(), candidate.requestId())
                .orElseThrow(() -> new IllegalStateException("面试认领后无法按请求身份读取，可能发生interviewId冲突"));
    }

    @Override
    public Optional<MockInterviewSession> findById(ActorId ownerId, UUID interviewId) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        LambdaQueryWrapper<MockInterviewSessionEntity> query = new LambdaQueryWrapper<>();
        query.eq(MockInterviewSessionEntity::getOwnerId, ownerId.value())
                .eq(MockInterviewSessionEntity::getInterviewId, interviewId.toString());
        return Optional.ofNullable(mapper.selectOne(query)).map(converter::toDomain);
    }

    @Override
    public Optional<MockInterviewSession> findByRequestId(ActorId ownerId, UUID requestId) {
        requireOwnerAndId(ownerId, requestId, "requestId");
        LambdaQueryWrapper<MockInterviewSessionEntity> query = new LambdaQueryWrapper<>();
        query.eq(MockInterviewSessionEntity::getOwnerId, ownerId.value())
                .eq(MockInterviewSessionEntity::getRequestId, requestId.toString());
        return Optional.ofNullable(mapper.selectOne(query)).map(converter::toDomain);
    }

    @Override
    public boolean updateIfVersionMatches(
            ActorId ownerId,
            MockInterviewSession updatedSession,
            long expectedVersion
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(updatedSession, "updatedSession不能为空");
        if (!ownerId.equals(updatedSession.ownerId())) {
            throw new IllegalArgumentException("ownerId与面试归属不一致");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion不能小于0");
        }
        if (updatedSession.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("更新后的version必须比旧version增加1");
        }

        int affectedRows = mapper.updateIfVersionMatches(
                updatedSession.interviewId().toString(),
                ownerId.value(),
                updatedSession.status().name(),
                updatedSession.failureCode() == null ? null : updatedSession.failureCode().name(),
                updatedSession.version(),
                updatedSession.updatedAt(),
                updatedSession.finishedAt(),
                expectedVersion
        );
        if (affectedRows > 1) throw new IllegalStateException("面试CAS更新影响了多行数据");
        return affectedRows == 1;
    }

    @Override
    public List<MockInterviewSession> findExecutionRequiredUpdatedBefore(ActorId ownerId, Instant updatedBefore, int limit) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(updatedBefore, "updatedBefore不能为空");
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit必须在1到1000之间");

        LambdaQueryWrapper<MockInterviewSessionEntity> query = new LambdaQueryWrapper<>();
        query.eq(MockInterviewSessionEntity::getOwnerId, ownerId.value())
                .in(MockInterviewSessionEntity::getInterviewStatus,
                        InterviewStatus.GENERATING_QUESTION.name(),
                        InterviewStatus.REVIEWING.name(),
                        InterviewStatus.GENERATING_REPORT.name())
                .lt(MockInterviewSessionEntity::getUpdatedAt, updatedBefore)
                .orderByAsc(MockInterviewSessionEntity::getUpdatedAt)
                .last("LIMIT " + limit);
        return mapper.selectList(query).stream().map(converter::toDomain).toList();
    }

    private static void requireOwnerAndId(ActorId ownerId, UUID id, String fieldName) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(id, fieldName + "不能为空");
    }
}