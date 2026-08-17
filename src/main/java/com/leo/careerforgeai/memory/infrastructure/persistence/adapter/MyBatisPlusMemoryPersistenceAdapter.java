package com.leo.careerforgeai.memory.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.careerforgeai.memory.application.port.profile.MemoryDecisionRepository;
import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecision;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemoryStatus;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.memory.infrastructure.persistence.converter.MemoryPersistenceConverter;
import com.leo.careerforgeai.memory.infrastructure.persistence.entity.MemoryDecisionEntity;
import com.leo.careerforgeai.memory.infrastructure.persistence.entity.MemoryItemEntity;
import com.leo.careerforgeai.memory.infrastructure.persistence.mapper.MemoryDecisionMapper;
import com.leo.careerforgeai.memory.infrastructure.persistence.mapper.MemoryItemMapper;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus实现Memory当前状态和决策审计的持久化端口
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Repository
@ConditionalOnProperty(
        prefix = "careerforge.persistence",
        name = "enabled",
        havingValue = "true"
)
public class MyBatisPlusMemoryPersistenceAdapter
        implements MemoryRepository, MemoryDecisionRepository {

    private final MemoryItemMapper memoryItemMapper;
    private final MemoryDecisionMapper memoryDecisionMapper;
    private final MemoryPersistenceConverter converter;

    public MyBatisPlusMemoryPersistenceAdapter(
            MemoryItemMapper memoryItemMapper,
            MemoryDecisionMapper memoryDecisionMapper,
            MemoryPersistenceConverter converter
    ) {
        this.memoryItemMapper = Objects.requireNonNull(
                memoryItemMapper,
                "memoryItemMapper 不能为空"
        );
        this.memoryDecisionMapper = Objects.requireNonNull(
                memoryDecisionMapper,
                "memoryDecisionMapper 不能为空"
        );
        this.converter = Objects.requireNonNull(
                converter,
                "converter 不能为空"
        );
    }

    @Override
    public void insert(MemoryItem memoryItem) {
        int affectedRows = memoryItemMapper.insert(
                converter.toEntity(memoryItem)
        );

        requireSingleAffectedRow(
                affectedRows,
                "插入Memory失败"
        );
    }

    @Override
    public Optional<MemoryItem> findById(
            ActorId ownerId,
            UUID memoryId
    ) {
        requireOwnerAndId(ownerId, memoryId);

        LambdaQueryWrapper<MemoryItemEntity> query =
                new LambdaQueryWrapper<>();

        query.eq(
                MemoryItemEntity::getOwnerId,
                ownerId.value()
        ).eq(
                MemoryItemEntity::getMemoryId,
                memoryId.toString()
        );

        return Optional.ofNullable(
                memoryItemMapper.selectOne(query)
        ).map(converter::toDomain);
    }

    @Override
    public List<MemoryItem> findPendingByOwner(ActorId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId 不能为空");

        LambdaQueryWrapper<MemoryItemEntity> query = new LambdaQueryWrapper<>();
        query.eq(MemoryItemEntity::getOwnerId, ownerId.value())
                .eq(MemoryItemEntity::getMemoryStatus, MemoryStatus.PENDING.name())
                .orderByAsc(MemoryItemEntity::getCreatedAt)
                .orderByAsc(MemoryItemEntity::getMemoryId);

        return memoryItemMapper.selectList(query)
                .stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public List<MemoryItem> findConfirmedByOwner(
            ActorId ownerId
    ) {
        Objects.requireNonNull(ownerId, "ownerId 不能为空");

        LambdaQueryWrapper<MemoryItemEntity> query =
                new LambdaQueryWrapper<>();

        query.eq(
                MemoryItemEntity::getOwnerId,
                ownerId.value()
        ).eq(
                MemoryItemEntity::getMemoryStatus,
                MemoryStatus.CONFIRMED.name()
        ).orderByAsc(
                MemoryItemEntity::getCreatedAt
        );

        return memoryItemMapper.selectList(query)
                .stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public List<MemoryItem> findByOwnerAndNormalizedKey(
            ActorId ownerId,
            MemoryType type,
            MemoryNormalizedKey normalizedKey
    ) {
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(type, "type 不能为空");
        Objects.requireNonNull(
                normalizedKey,
                "normalizedKey 不能为空"
        );

        if (!normalizedKey.supports(type)) {
            throw new IllegalArgumentException(
                    "normalizedKey与Memory类型不匹配"
            );
        }

        LambdaQueryWrapper<MemoryItemEntity> query =
                new LambdaQueryWrapper<>();

        query.eq(
                MemoryItemEntity::getOwnerId,
                ownerId.value()
        ).eq(
                MemoryItemEntity::getMemoryType,
                type.name()
        ).eq(
                MemoryItemEntity::getNormalizedKey,
                normalizedKey.value()
        ).eq(
                MemoryItemEntity::getNormalizationVersion,
                normalizedKey.normalizationVersion()
        ).orderByDesc(
                MemoryItemEntity::getUpdatedAt
        );

        return memoryItemMapper.selectList(query)
                .stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public boolean updateIfVersionMatches(
            ActorId ownerId,
            MemoryItem updatedMemory,
            long expectedVersion
    ) {
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(
                updatedMemory,
                "updatedMemory 不能为空"
        );

        if (!ownerId.equals(updatedMemory.ownerId())) {
            throw new IllegalArgumentException(
                    "ownerId与Memory归属不一致"
            );
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "expectedVersion不能小于0"
            );
        }
        if (updatedMemory.version() != expectedVersion + 1) {
            throw new IllegalArgumentException(
                    "更新后的version必须比旧version增加1"
            );
        }

        int affectedRows =
                memoryItemMapper.updateStateIfVersionMatches(
                        updatedMemory.memoryId().toString(),
                        ownerId.value(),
                        updatedMemory.status().name(),
                        updatedMemory.version(),
                        updatedMemory.updatedAt(),
                        expectedVersion
                );

        if (affectedRows > 1) {
            throw new IllegalStateException(
                    "Memory乐观锁更新影响了多行数据"
            );
        }

        return affectedRows == 1;
    }

    @Override
    public void insert(MemoryDecision decision) {
        int affectedRows = memoryDecisionMapper.insert(
                converter.toEntity(decision)
        );

        requireSingleAffectedRow(
                affectedRows,
                "插入Memory决策失败"
        );
    }

    @Override
    public List<MemoryDecision> findByMemoryId(
            ActorId ownerId,
            UUID memoryId
    ) {
        requireOwnerAndId(ownerId, memoryId);

        LambdaQueryWrapper<MemoryDecisionEntity> query =
                new LambdaQueryWrapper<>();

        query.eq(
                MemoryDecisionEntity::getOwnerId,
                ownerId.value()
        ).eq(
                MemoryDecisionEntity::getMemoryId,
                memoryId.toString()
        ).orderByAsc(
                MemoryDecisionEntity::getDecidedAt
        ).orderByAsc(
                MemoryDecisionEntity::getDecisionId
        );

        return memoryDecisionMapper.selectList(query)
                .stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public long countSkillProfileChanges(ActorId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        long profileVersion = memoryDecisionMapper.countSkillProfileChanges(ownerId.value());
        if (profileVersion < 0) {
            throw new IllegalStateException("技能画像版本不能小于0");
        }
        return profileVersion;
    }

    private static void requireOwnerAndId(
            ActorId ownerId,
            UUID memoryId
    ) {
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(memoryId, "memoryId 不能为空");
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