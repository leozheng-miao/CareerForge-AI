package com.leo.careerforgeai.interview.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.careerforgeai.interview.application.port.MockInterviewInputSnapshotRepository;
import com.leo.careerforgeai.interview.domain.MockInterviewInputSnapshot;
import com.leo.careerforgeai.interview.infrastructure.persistence.converter.MockInterviewInputSnapshotPersistenceConverter;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.MockInterviewInputArtifactEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.MockInterviewInputSnapshotEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.mapper.MockInterviewInputArtifactMapper;
import com.leo.careerforgeai.interview.infrastructure.persistence.mapper.MockInterviewInputSnapshotMapper;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis-Plus原子保存和owner隔离读取模拟面试冻结输入快照
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Repository
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public final class MyBatisPlusMockInterviewInputSnapshotAdapter
        implements MockInterviewInputSnapshotRepository {

    private final MockInterviewInputSnapshotMapper snapshotMapper;
    private final MockInterviewInputArtifactMapper artifactMapper;
    private final MockInterviewInputSnapshotPersistenceConverter converter;

    public MyBatisPlusMockInterviewInputSnapshotAdapter(
            MockInterviewInputSnapshotMapper snapshotMapper,
            MockInterviewInputArtifactMapper artifactMapper,
            MockInterviewInputSnapshotPersistenceConverter converter
    ) {
        this.snapshotMapper = Objects.requireNonNull(snapshotMapper, "snapshotMapper不能为空");
        this.artifactMapper = Objects.requireNonNull(artifactMapper, "artifactMapper不能为空");
        this.converter = Objects.requireNonNull(converter, "converter不能为空");
    }

    @Override
    @Transactional
    public MockInterviewInputSnapshot claim(MockInterviewInputSnapshot candidate) {
        Objects.requireNonNull(candidate, "candidate不能为空");
        snapshotMapper.claim(converter.toEntity(candidate));

        MockInterviewInputSnapshotEntity stored = findEntityByHash(
                candidate.ownerId(),
                candidate.snapshotHash()
        ).orElseThrow(() -> new IllegalStateException(
                "输入快照认领后无法按owner和hash读取，可能发生inputSnapshotId冲突"
        ));

        if (!stored.getInputSnapshotId().equals(candidate.inputSnapshotId().toString())) {
            return loadCompleteSnapshot(stored);
        }

        for (MockInterviewInputSnapshot.ArtifactReference reference : candidate.artifactReferences()) {
            artifactMapper.claim(converter.toEntity(candidate, reference));
        }
        return loadCompleteSnapshot(stored);
    }

    @Override
    public Optional<MockInterviewInputSnapshot> findById(ActorId ownerId, UUID inputSnapshotId) {
        requireOwnerAndId(ownerId, inputSnapshotId, "inputSnapshotId");
        LambdaQueryWrapper<MockInterviewInputSnapshotEntity> query = new LambdaQueryWrapper<>();
        query.eq(MockInterviewInputSnapshotEntity::getOwnerId, ownerId.value())
                .eq(MockInterviewInputSnapshotEntity::getInputSnapshotId, inputSnapshotId.toString());
        return Optional.ofNullable(snapshotMapper.selectOne(query)).map(this::loadCompleteSnapshot);
    }

    @Override
    public Optional<MockInterviewInputSnapshot> findByHash(ActorId ownerId, String snapshotHash) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        if (snapshotHash == null || snapshotHash.isBlank()) {
            throw new IllegalArgumentException("snapshotHash不能为空");
        }
        return findEntityByHash(ownerId, snapshotHash).map(this::loadCompleteSnapshot);
    }

    private Optional<MockInterviewInputSnapshotEntity> findEntityByHash(
            ActorId ownerId,
            String snapshotHash
    ) {
        LambdaQueryWrapper<MockInterviewInputSnapshotEntity> query = new LambdaQueryWrapper<>();
        query.eq(MockInterviewInputSnapshotEntity::getOwnerId, ownerId.value())
                .eq(MockInterviewInputSnapshotEntity::getSnapshotHash, snapshotHash);
        return Optional.ofNullable(snapshotMapper.selectOne(query));
    }

    private MockInterviewInputSnapshot loadCompleteSnapshot(
            MockInterviewInputSnapshotEntity snapshot
    ) {
        List<MockInterviewInputArtifactEntity> artifacts = artifactMapper.findBySnapshot(
                snapshot.getOwnerId(),
                snapshot.getInputSnapshotId()
        );
        return converter.toDomain(snapshot, artifacts);
    }

    private static void requireOwnerAndId(ActorId ownerId, UUID id, String fieldName) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(id, fieldName + "不能为空");
    }
}