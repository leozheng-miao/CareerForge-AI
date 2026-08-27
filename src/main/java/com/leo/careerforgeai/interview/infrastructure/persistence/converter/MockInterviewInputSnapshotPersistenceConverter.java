
package com.leo.careerforgeai.interview.infrastructure.persistence.converter;

import com.leo.careerforgeai.interview.domain.MockInterviewInputSnapshot;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.MockInterviewInputArtifactEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.MockInterviewInputSnapshotEntity;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 转换模拟面试冻结输入快照领域对象及其数据库Entity
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Component
public final class MockInterviewInputSnapshotPersistenceConverter {

    public MockInterviewInputSnapshotEntity toEntity(MockInterviewInputSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot不能为空");

        MockInterviewInputSnapshotEntity entity = new MockInterviewInputSnapshotEntity();
        entity.setInputSnapshotId(snapshot.inputSnapshotId().toString());
        entity.setOwnerId(snapshot.ownerId().value());
        entity.setSchemaVersion(snapshot.schemaVersion());
        entity.setTargetRoleId(snapshot.targetRoleId().toString());
        entity.setTargetRoleVersion(snapshot.targetRoleVersion());
        entity.setSkillGapSnapshotId(toNullableString(snapshot.skillGapSnapshotId()));
        entity.setTrainingPlanId(toNullableString(snapshot.trainingPlanId()));
        entity.setTrainingPlanVersion(snapshot.trainingPlanVersion());
        entity.setSnapshotContextJson(snapshot.snapshotContextJson());
        entity.setSnapshotHash(snapshot.snapshotHash());
        entity.setCreatedAt(snapshot.createdAt());
        return entity;
    }

    public MockInterviewInputArtifactEntity toEntity(
            MockInterviewInputSnapshot snapshot,
            MockInterviewInputSnapshot.ArtifactReference reference
    ) {
        MockInterviewInputArtifactEntity entity = new MockInterviewInputArtifactEntity();
        entity.setInputSnapshotId(snapshot.inputSnapshotId().toString());
        entity.setOwnerId(snapshot.ownerId().value());
        entity.setArtifactId(reference.artifactId().toString());
        entity.setArtifactVersion(reference.artifactVersion());
        entity.setArtifactSourceHash(reference.artifactSourceHash());
        entity.setArtifactOrder(reference.artifactOrder());
        entity.setCreatedAt(snapshot.createdAt());
        return entity;
    }

    public MockInterviewInputSnapshot toDomain(
            MockInterviewInputSnapshotEntity snapshot,
            List<MockInterviewInputArtifactEntity> artifacts
    ) {
        Objects.requireNonNull(snapshot, "snapshot不能为空");
        Objects.requireNonNull(artifacts, "artifacts不能为空");

        for (MockInterviewInputArtifactEntity artifact : artifacts) {
            if (!Objects.equals(snapshot.getInputSnapshotId(), artifact.getInputSnapshotId())
                    || !Objects.equals(snapshot.getOwnerId(), artifact.getOwnerId())) {
                throw new IllegalStateException("数据库证据引用不属于当前输入快照");
            }
        }

        List<MockInterviewInputSnapshot.ArtifactReference> references = artifacts.stream()
                .map(this::toDomain)
                .toList();

        return new MockInterviewInputSnapshot(
                UUID.fromString(snapshot.getInputSnapshotId()),
                new ActorId(snapshot.getOwnerId()),
                requireInteger(snapshot.getSchemaVersion(), "schemaVersion"),
                UUID.fromString(snapshot.getTargetRoleId()),
                requireLong(snapshot.getTargetRoleVersion(), "targetRoleVersion"),
                toNullableUuid(snapshot.getSkillGapSnapshotId()),
                toNullableUuid(snapshot.getTrainingPlanId()),
                snapshot.getTrainingPlanVersion(),
                snapshot.getSnapshotContextJson(),
                references,
                snapshot.getSnapshotHash(),
                snapshot.getCreatedAt()
        );
    }

    private MockInterviewInputSnapshot.ArtifactReference toDomain(
            MockInterviewInputArtifactEntity entity
    ) {
        return new MockInterviewInputSnapshot.ArtifactReference(
                UUID.fromString(entity.getArtifactId()),
                requireLong(entity.getArtifactVersion(), "artifactVersion"),
                entity.getArtifactSourceHash(),
                requireInteger(entity.getArtifactOrder(), "artifactOrder")
        );
    }

    private static String toNullableString(UUID value) {
        return value == null ? null : value.toString();
    }

    private static UUID toNullableUuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private static int requireInteger(Integer value, String fieldName) {
        if (value == null) throw new IllegalStateException("数据库" + fieldName + "不能为空");
        return value;
    }

    private static long requireLong(Long value, String fieldName) {
        if (value == null) throw new IllegalStateException("数据库" + fieldName + "不能为空");
        return value;
    }
}