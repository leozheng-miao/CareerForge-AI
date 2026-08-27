package com.leo.careerforgeai.interview.infrastructure.persistence.converter;

import com.leo.careerforgeai.interview.domain.PersonalEvidenceArtifact;
import com.leo.careerforgeai.interview.domain.PersonalEvidenceStatus;
import com.leo.careerforgeai.interview.domain.PersonalEvidenceType;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.PersonalEvidenceArtifactEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.PersonalEvidenceChunkEntity;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 转换个人证据聚合及其两张持久化表
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Component
public class PersonalEvidencePersistenceConverter {

    public PersonalEvidenceArtifactEntity toEntity(PersonalEvidenceArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact不能为空");

        PersonalEvidenceArtifactEntity entity = new PersonalEvidenceArtifactEntity();
        entity.setArtifactId(artifact.artifactId().toString());
        entity.setArtifactVersion(artifact.artifactVersion());
        entity.setOwnerId(artifact.ownerId().value());
        entity.setArtifactType(artifact.type().name());
        entity.setSourceName(artifact.sourceName());
        entity.setSourceHash(artifact.sourceHash());
        entity.setContent(artifact.content());
        entity.setArtifactStatus(artifact.status().name());
        entity.setSupersededByVersion(artifact.supersededByVersion());
        entity.setCreatedAt(artifact.createdAt());
        entity.setUpdatedAt(artifact.updatedAt());
        entity.setSupersededAt(artifact.supersededAt());
        entity.setRevokedAt(artifact.revokedAt());
        return entity;
    }

    public PersonalEvidenceChunkEntity toEntity(
            PersonalEvidenceArtifact artifact,
            PersonalEvidenceArtifact.Chunk chunk
    ) {
        Objects.requireNonNull(artifact, "artifact不能为空");
        Objects.requireNonNull(chunk, "chunk不能为空");

        PersonalEvidenceChunkEntity entity = new PersonalEvidenceChunkEntity();
        entity.setEvidenceChunkId(chunk.evidenceChunkId());
        entity.setArtifactId(artifact.artifactId().toString());
        entity.setArtifactVersion(artifact.artifactVersion());
        entity.setOwnerId(artifact.ownerId().value());
        entity.setChunkIndex(chunk.chunkIndex());
        entity.setStartOffset(chunk.startOffset());
        entity.setEndOffset(chunk.endOffset());
        entity.setChunkContent(chunk.chunkContent());
        entity.setContentHash(chunk.contentHash());
        entity.setCreatedAt(chunk.createdAt());
        return entity;
    }

    public PersonalEvidenceArtifact toDomain(
            PersonalEvidenceArtifactEntity artifact,
            List<PersonalEvidenceChunkEntity> chunks
    ) {
        Objects.requireNonNull(artifact, "artifact不能为空");
        Objects.requireNonNull(chunks, "chunks不能为空");

        for (PersonalEvidenceChunkEntity chunk : chunks) {
            if (!Objects.equals(artifact.getArtifactId(), chunk.getArtifactId())
                    || !Objects.equals(artifact.getArtifactVersion(), chunk.getArtifactVersion())
                    || !Objects.equals(artifact.getOwnerId(), chunk.getOwnerId())) {
                throw new IllegalStateException("数据库证据片段不属于当前证据版本");
            }
        }

        List<PersonalEvidenceArtifact.Chunk> domainChunks = chunks.stream()
                .map(this::toDomain)
                .toList();

        return new PersonalEvidenceArtifact(
                UUID.fromString(artifact.getArtifactId()),
                requireLong(artifact.getArtifactVersion(), "artifactVersion"),
                new ActorId(artifact.getOwnerId()),
                PersonalEvidenceType.valueOf(artifact.getArtifactType()),
                artifact.getSourceName(),
                artifact.getSourceHash(),
                artifact.getContent(),
                PersonalEvidenceStatus.valueOf(artifact.getArtifactStatus()),
                artifact.getSupersededByVersion(),
                domainChunks,
                artifact.getCreatedAt(),
                artifact.getUpdatedAt(),
                artifact.getSupersededAt(),
                artifact.getRevokedAt()
        );
    }

    private PersonalEvidenceArtifact.Chunk toDomain(PersonalEvidenceChunkEntity chunk) {
        return new PersonalEvidenceArtifact.Chunk(
                chunk.getEvidenceChunkId(),
                requireInteger(chunk.getChunkIndex(), "chunkIndex"),
                requireInteger(chunk.getStartOffset(), "startOffset"),
                requireInteger(chunk.getEndOffset(), "endOffset"),
                chunk.getChunkContent(),
                chunk.getContentHash(),
                chunk.getCreatedAt()
        );
    }

    private static long requireLong(Long value, String fieldName) {
        if (value == null) throw new IllegalStateException("数据库" + fieldName + "不能为空");
        return value;
    }

    private static int requireInteger(Integer value, String fieldName) {
        if (value == null) throw new IllegalStateException("数据库" + fieldName + "不能为空");
        return value;
    }
}