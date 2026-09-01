package com.leo.careerforgeai.interview.application.port;

import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceArtifact;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义个人证据创建、版本替换、撤销和owner隔离查询边界
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public interface PersonalEvidenceArtifactRepository {

    PersonalEvidenceArtifact create(PersonalEvidenceArtifact artifact);

    Optional<PersonalEvidenceArtifact> findActive(ActorId ownerId, UUID artifactId);

    Optional<PersonalEvidenceArtifact> findVersion(ActorId ownerId, UUID artifactId, long artifactVersion);

    boolean replaceActiveIfVersionMatches(
            ActorId ownerId,
            PersonalEvidenceArtifact supersededArtifact,
            PersonalEvidenceArtifact replacement,
            long expectedVersion
    );

    boolean revokeActiveIfVersionMatches(
            ActorId ownerId,
            PersonalEvidenceArtifact revokedArtifact,
            long expectedVersion
    );

    Optional<PersonalEvidenceArtifact> findVersionForSnapshot(
            ActorId ownerId,
            UUID artifactId,
            long artifactVersion
    );
}