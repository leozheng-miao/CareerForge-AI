package com.leo.careerforgeai.interview.infrastructure.persistence.adapter;

import com.leo.careerforgeai.interview.application.port.PersonalEvidenceArtifactRepository;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceArtifact;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceStatus;
import com.leo.careerforgeai.interview.infrastructure.persistence.converter.PersonalEvidencePersistenceConverter;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.PersonalEvidenceArtifactEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.mapper.PersonalEvidenceFactMapper;
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
 * @description: 事务保存个人证据版本和片段并执行owner受控生命周期CAS
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Repository
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MyBatisPersonalEvidenceArtifactAdapter
        implements PersonalEvidenceArtifactRepository {

    private final PersonalEvidenceFactMapper mapper;
    private final PersonalEvidencePersistenceConverter converter;

    public MyBatisPersonalEvidenceArtifactAdapter(
            PersonalEvidenceFactMapper mapper,
            PersonalEvidencePersistenceConverter converter
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper不能为空");
        this.converter = Objects.requireNonNull(converter, "converter不能为空");
    }

    @Override
    @Transactional
    public PersonalEvidenceArtifact create(PersonalEvidenceArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact不能为空");
        if (artifact.artifactVersion() != 1 || artifact.status() != PersonalEvidenceStatus.ACTIVE) {
            throw new IllegalArgumentException("新证据必须是version=1的ACTIVE版本");
        }

        insertArtifactAndChunks(artifact);
        return findVersion(artifact.ownerId(), artifact.artifactId(), artifact.artifactVersion())
                .orElseThrow(() -> new IllegalStateException("个人证据创建后无法读取"));
    }

    @Override
    public Optional<PersonalEvidenceArtifact> findActive(ActorId ownerId, UUID artifactId) {
        requireOwnerAndId(ownerId, artifactId);
        return Optional.ofNullable(mapper.findActive(ownerId.value(), artifactId.toString()))
                .map(this::loadCompleteArtifact);
    }

    @Override
    public Optional<PersonalEvidenceArtifact> findVersion(
            ActorId ownerId,
            UUID artifactId,
            long artifactVersion
    ) {
        requireOwnerAndId(ownerId, artifactId);
        if (artifactVersion < 1) throw new IllegalArgumentException("artifactVersion必须从1开始");

        return Optional.ofNullable(mapper.findVersion(
                ownerId.value(),
                artifactId.toString(),
                artifactVersion
        )).map(this::loadCompleteArtifact);
    }

    @Override
    @Transactional
    public boolean replaceActiveIfVersionMatches(
            ActorId ownerId,
            PersonalEvidenceArtifact supersededArtifact,
            PersonalEvidenceArtifact replacement,
            long expectedVersion
    ) {
        validateReplacement(ownerId, supersededArtifact, replacement, expectedVersion);

        List<Long> activeVersions = mapper.lockActiveVersions(
                ownerId.value(),
                supersededArtifact.artifactId().toString()
        );
        validateSingleActiveVersion(activeVersions);
        if (activeVersions.isEmpty() || activeVersions.getFirst() != expectedVersion) return false;

        insertArtifactAndChunks(replacement);
        int affectedRows = mapper.updateActiveLifecycle(
                converter.toEntity(supersededArtifact),
                expectedVersion
        );
        if (affectedRows != 1) {
            throw new IllegalStateException("个人证据替代时ACTIVE版本CAS更新失败");
        }
        return true;
    }

    @Override
    @Transactional
    public boolean revokeActiveIfVersionMatches(
            ActorId ownerId,
            PersonalEvidenceArtifact revokedArtifact,
            long expectedVersion
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(revokedArtifact, "revokedArtifact不能为空");

        if (!ownerId.equals(revokedArtifact.ownerId())) {
            throw new IllegalArgumentException("ownerId与个人证据归属不一致");
        }
        if (revokedArtifact.status() != PersonalEvidenceStatus.REVOKED
                || revokedArtifact.artifactVersion() != expectedVersion) {
            throw new IllegalArgumentException("撤销对象的状态或版本不合法");
        }

        int affectedRows = mapper.updateActiveLifecycle(
                converter.toEntity(revokedArtifact),
                expectedVersion
        );
        if (affectedRows > 1) throw new IllegalStateException("个人证据撤销CAS影响了多行数据");
        return affectedRows == 1;
    }

    @Override
    public Optional<PersonalEvidenceArtifact> findVersionForSnapshot(
            ActorId ownerId,
            UUID artifactId,
            long artifactVersion
    ) {
        requireOwnerAndId(ownerId, artifactId);
        if (artifactVersion < 1) throw new IllegalArgumentException("artifactVersion必须从1开始");

        return Optional.ofNullable(mapper.lockVersionForSnapshot(
                ownerId.value(),
                artifactId.toString(),
                artifactVersion
        )).map(this::loadCompleteArtifact);
    }

    private void insertArtifactAndChunks(PersonalEvidenceArtifact artifact) {
        if (mapper.insertArtifact(converter.toEntity(artifact)) != 1) {
            throw new IllegalStateException("个人证据版本写入失败");
        }
        for (PersonalEvidenceArtifact.Chunk chunk : artifact.chunks()) {
            if (mapper.insertChunk(converter.toEntity(artifact, chunk)) != 1) {
                throw new IllegalStateException("个人证据片段写入失败");
            }
        }
    }

    private PersonalEvidenceArtifact loadCompleteArtifact(PersonalEvidenceArtifactEntity artifact) {
        return converter.toDomain(
                artifact,
                mapper.findChunks(
                        artifact.getOwnerId(),
                        artifact.getArtifactId(),
                        requireVersion(artifact.getArtifactVersion())
                )
        );
    }

    private static void validateReplacement(
            ActorId ownerId,
            PersonalEvidenceArtifact superseded,
            PersonalEvidenceArtifact replacement,
            long expectedVersion
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(superseded, "supersededArtifact不能为空");
        Objects.requireNonNull(replacement, "replacement不能为空");

        if (!ownerId.equals(superseded.ownerId()) || !ownerId.equals(replacement.ownerId())) {
            throw new IllegalArgumentException("ownerId与个人证据归属不一致");
        }
        if (!superseded.artifactId().equals(replacement.artifactId())) {
            throw new IllegalArgumentException("新旧版本artifactId必须一致");
        }
        if (superseded.status() != PersonalEvidenceStatus.SUPERSEDED
                || replacement.status() != PersonalEvidenceStatus.ACTIVE) {
            throw new IllegalArgumentException("新旧版本状态不符合替代规则");
        }
        if (expectedVersion < 1
                || superseded.artifactVersion() != expectedVersion
                || replacement.artifactVersion() != expectedVersion + 1
                || !Objects.equals(superseded.supersededByVersion(), replacement.artifactVersion())) {
            throw new IllegalArgumentException("新旧版本不符合连续递增规则");
        }
        if (replacement.createdAt().isBefore(superseded.updatedAt())) {
            throw new IllegalArgumentException("新版本创建时间不能早于旧版本更新时间");
        }
    }

    private static void validateSingleActiveVersion(List<Long> activeVersions) {
        Objects.requireNonNull(activeVersions, "activeVersions不能为空");
        if (activeVersions.size() > 1) {
            throw new IllegalStateException("同一个人证据存在多个ACTIVE版本");
        }
    }

    private static long requireVersion(Long version) {
        if (version == null) throw new IllegalStateException("数据库artifactVersion不能为空");
        return version;
    }

    private static void requireOwnerAndId(ActorId ownerId, UUID artifactId) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(artifactId, "artifactId不能为空");
    }
}