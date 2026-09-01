package com.leo.careerforgeai.interview.application.evidence;

import com.leo.careerforgeai.interview.application.port.PersonalEvidenceArtifactRepository;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceArtifact;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceType;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 使用当前Actor执行个人证据创建、版本更新、查询和撤销
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Service
@ConditionalOnBean(PersonalEvidenceArtifactRepository.class)
public class PersonalEvidenceApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final PersonalEvidenceArtifactRepository repository;
    private final PersonalEvidenceArtifactFactory factory;
    private final Clock clock;

    public PersonalEvidenceApplicationService(
            CurrentActorProvider currentActorProvider,
            PersonalEvidenceArtifactRepository repository,
            PersonalEvidenceArtifactFactory factory,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.factory = Objects.requireNonNull(factory, "factory不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public PersonalEvidenceArtifact create(
            PersonalEvidenceType type,
            String sourceName,
            String rawContent
    ) {
        ActorId ownerId = currentActor();
        Instant now = clock.instant();
        PersonalEvidenceArtifact artifact = factory.create(
                UUID.randomUUID(),
                1,
                ownerId,
                type,
                sourceName,
                rawContent,
                now
        );
        return requireOwnedResult(ownerId, repository.create(artifact));
    }

    @Transactional(readOnly = true)
    public PersonalEvidenceArtifact get(UUID artifactId) {
        ActorId ownerId = currentActor();
        return requireOwnedActive(ownerId, artifactId);
    }

    @Transactional(readOnly = true)
    public PersonalEvidenceArtifact getVersion(UUID artifactId, long artifactVersion) {
        Objects.requireNonNull(artifactId, "artifactId不能为空");
        if (artifactVersion < 1) throw new IllegalArgumentException("artifactVersion必须从1开始");

        ActorId ownerId = currentActor();
        PersonalEvidenceArtifact artifact = repository.findVersion(ownerId, artifactId, artifactVersion)
                .orElseThrow(() -> new PersonalEvidenceNotFoundException(artifactId));
        return requireOwnedResult(ownerId, artifact);
    }

    @Transactional
    public PersonalEvidenceArtifact update(
            UUID artifactId,
            long expectedVersion,
            String sourceName,
            String rawContent
    ) {
        requireExpectedVersion(artifactId, expectedVersion);

        ActorId ownerId = currentActor();
        PersonalEvidenceArtifact current = requireOwnedActive(ownerId, artifactId);
        if (current.artifactVersion() != expectedVersion) {
            throw new PersonalEvidenceVersionConflictException(artifactId, expectedVersion);
        }

        Instant now = clock.instant();
        PersonalEvidenceArtifact replacement = factory.create(
                artifactId,
                expectedVersion + 1,
                ownerId,
                current.type(),
                sourceName,
                rawContent,
                now
        );
        PersonalEvidenceArtifact superseded = current.supersede(replacement.artifactVersion(), now);

        if (!repository.replaceActiveIfVersionMatches(
                ownerId,
                superseded,
                replacement,
                expectedVersion
        )) {
            throw new PersonalEvidenceVersionConflictException(artifactId, expectedVersion);
        }
        return replacement;
    }

    @Transactional
    public PersonalEvidenceArtifact revoke(UUID artifactId, long expectedVersion) {
        requireExpectedVersion(artifactId, expectedVersion);

        ActorId ownerId = currentActor();
        PersonalEvidenceArtifact current = requireOwnedActive(ownerId, artifactId);
        if (current.artifactVersion() != expectedVersion) {
            throw new PersonalEvidenceVersionConflictException(artifactId, expectedVersion);
        }

        PersonalEvidenceArtifact revoked = current.revoke(clock.instant());
        if (!repository.revokeActiveIfVersionMatches(ownerId, revoked, expectedVersion)) {
            throw new PersonalEvidenceVersionConflictException(artifactId, expectedVersion);
        }
        return revoked;
    }

    private PersonalEvidenceArtifact requireOwnedActive(ActorId ownerId, UUID artifactId) {
        Objects.requireNonNull(artifactId, "artifactId不能为空");
        PersonalEvidenceArtifact artifact = repository.findActive(ownerId, artifactId)
                .orElseThrow(() -> new PersonalEvidenceNotFoundException(artifactId));
        return requireOwnedResult(ownerId, artifact);
    }

    private static PersonalEvidenceArtifact requireOwnedResult(
            ActorId ownerId,
            PersonalEvidenceArtifact artifact
    ) {
        Objects.requireNonNull(artifact, "repository不能返回null");
        if (!ownerId.equals(artifact.ownerId())) {
            throw new IllegalStateException("个人证据查询结果违反owner边界");
        }
        return artifact;
    }

    private static void requireExpectedVersion(UUID artifactId, long expectedVersion) {
        Objects.requireNonNull(artifactId, "artifactId不能为空");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion必须从1开始");
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }
}