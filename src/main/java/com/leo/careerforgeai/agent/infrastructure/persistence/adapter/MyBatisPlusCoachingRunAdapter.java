package com.leo.careerforgeai.agent.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.agent.infrastructure.persistence.converter.CoachingRunPersistenceConverter;
import com.leo.careerforgeai.agent.infrastructure.persistence.entity.CoachingRunEntity;
import com.leo.careerforgeai.agent.infrastructure.persistence.mapper.CoachingRunMapper;
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
 * @description: 使用MyBatis-Plus实现Coaching Run插入、owner隔离查询和CAS状态更新
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Repository
@ConditionalOnProperty(
        prefix = "careerforge.persistence",
        name = "enabled",
        havingValue = "true"
)
public class MyBatisPlusCoachingRunAdapter
        implements CoachingRunRepository {

    private final CoachingRunMapper mapper;
    private final CoachingRunPersistenceConverter converter;

    public MyBatisPlusCoachingRunAdapter(
            CoachingRunMapper mapper,
            CoachingRunPersistenceConverter converter
    ) {
        this.mapper = Objects.requireNonNull(
                mapper,
                "mapper不能为空"
        );
        this.converter = Objects.requireNonNull(
                converter,
                "converter不能为空"
        );
    }

    @Override
    public CoachingRun claim(CoachingRun candidate) {
        Objects.requireNonNull(candidate, "candidate不能为空");

        mapper.claim(converter.toEntity(candidate));

        return findByRequestId(
                candidate.ownerId(),
                candidate.requestId()
        ).orElseThrow(() -> new IllegalStateException(
                "Run认领后无法按请求身份读取，可能发生runId冲突"
        ));
    }
    @Override
    public Optional<CoachingRun> findByRunId(
            ActorId ownerId,
            UUID runId
    ) {
        requireOwnerAndId(ownerId, runId, "runId");

        LambdaQueryWrapper<CoachingRunEntity> query =
                new LambdaQueryWrapper<>();
        query.eq(CoachingRunEntity::getOwnerId, ownerId.value())
                .eq(
                        CoachingRunEntity::getRunId,
                        runId.toString()
                );

        return Optional.ofNullable(mapper.selectOne(query))
                .map(converter::toDomain);
    }

    @Override
    public Optional<CoachingRun> findByRequestId(
            ActorId ownerId,
            UUID requestId
    ) {
        requireOwnerAndId(ownerId, requestId, "requestId");

        LambdaQueryWrapper<CoachingRunEntity> query =
                new LambdaQueryWrapper<>();
        query.eq(CoachingRunEntity::getOwnerId, ownerId.value())
                .eq(
                        CoachingRunEntity::getRequestId,
                        requestId.toString()
                );

        return Optional.ofNullable(mapper.selectOne(query))
                .map(converter::toDomain);
    }

    @Override
    public List<CoachingRun> findNonTerminalUpdatedBefore(
            Instant updatedBefore,
            int limit
    ) {
        Objects.requireNonNull(updatedBefore, "updatedBefore不能为空");
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit必须在1到1000之间");
        }

        LambdaQueryWrapper<CoachingRunEntity> query = new LambdaQueryWrapper<>();
        query.in(
                        CoachingRunEntity::getRunStatus,
                        CoachingRunStatus.RECEIVED.name(),
                        CoachingRunStatus.ACCEPTED.name(),
                        CoachingRunStatus.RUNNING.name()
                )
                .lt(CoachingRunEntity::getUpdatedAt, updatedBefore)
                .orderByAsc(CoachingRunEntity::getUpdatedAt)
                .last("LIMIT " + limit);

        return mapper.selectList(query).stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public boolean updateIfVersionMatches(
            ActorId ownerId,
            CoachingRun updatedRun,
            long expectedVersion
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(updatedRun, "updatedRun不能为空");

        if (!ownerId.equals(updatedRun.ownerId())) {
            throw new IllegalArgumentException(
                    "ownerId与Run归属不一致"
            );
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "expectedVersion不能小于0"
            );
        }
        if (updatedRun.version() != expectedVersion + 1) {
            throw new IllegalArgumentException(
                    "更新后的version必须比旧version增加1"
            );
        }

        int affectedRows = mapper.updateIfVersionMatches(
                updatedRun.runId().toString(),
                ownerId.value(),
                updatedRun.status().name(),
                toNullableString(updatedRun.userTurnId()),
                toNullableString(updatedRun.assistantTurnId()),
                updatedRun.failureCode(),
                updatedRun.version(),
                updatedRun.acceptedAt(),
                updatedRun.startedAt(),
                updatedRun.finishedAt(),
                updatedRun.updatedAt(),
                expectedVersion
        );

        if (affectedRows > 1) {
            throw new IllegalStateException(
                    "Run乐观锁更新影响了多行数据"
            );
        }
        return affectedRows == 1;
    }

    private static void requireOwnerAndId(
            ActorId ownerId,
            UUID id,
            String fieldName
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(
                id,
                fieldName + "不能为空"
        );
    }

    private static String toNullableString(UUID value) {
        return value == null ? null : value.toString();
    }

    private static void requireSingleAffectedRow(
            int affectedRows,
            String message
    ) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    message + ": affectedRows=" + affectedRows
            );
        }
    }
}