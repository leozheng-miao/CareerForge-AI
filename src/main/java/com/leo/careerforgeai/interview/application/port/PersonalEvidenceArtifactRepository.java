package com.leo.careerforgeai.interview.application.port;

import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceArtifact;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceType;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.List;
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

    List<ActiveArtifactSummary> findActivePage(
            ActorId ownerId,
            PersonalEvidenceType type,
            Instant beforeUpdatedAt,
            UUID beforeArtifactId,
            int limit
    );

    /**
     * @program: CareerForge-AI
     * @description: 当前用户可选择的ACTIVE个人证据轻量摘要
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param artifactId 证据ID
     * @param artifactVersion 当前ACTIVE版本
     * @param type 证据类型
     * @param sourceName 来源名称
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    record ActiveArtifactSummary(
            UUID artifactId,
            long artifactVersion,
            PersonalEvidenceType type,
            String sourceName,
            Instant createdAt,
            Instant updatedAt
    ) {}
}