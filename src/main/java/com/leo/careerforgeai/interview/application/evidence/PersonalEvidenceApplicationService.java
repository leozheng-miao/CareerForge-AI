package com.leo.careerforgeai.interview.application.evidence;

import com.leo.careerforgeai.interview.application.port.PersonalEvidenceArtifactRepository;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceArtifact;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceType;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
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

    @Transactional(readOnly = true)
    public EvidencePage list(PersonalEvidenceType type, String cursor, int limit) {
        if (limit < 1 || limit > 20) throw new IllegalArgumentException("limit必须在1到20之间");
        EvidenceCursor decoded = decodeCursor(cursor);
        String typeKey = type == null ? "*" : type.name();
        if (decoded != null && !decoded.typeKey().equals(typeKey)) {
            throw new IllegalArgumentException("cursor与当前证据类型过滤不匹配");
        }

        List<PersonalEvidenceArtifactRepository.ActiveArtifactSummary> rows = repository.findActivePage(
                currentActor(), type,
                decoded == null ? null : decoded.beforeUpdatedAt(),
                decoded == null ? null : decoded.beforeArtifactId(),
                limit + 1
        );
        boolean hasMore = rows.size() > limit;
        List<PersonalEvidenceArtifactRepository.ActiveArtifactSummary> items =
                List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
        return new EvidencePage(items, hasMore ? encodeCursor(items.getLast(), typeKey) : null, hasMore);
    }

    private static String encodeCursor(
            PersonalEvidenceArtifactRepository.ActiveArtifactSummary item,
            String typeKey
    ) {
        String value = typeKey + "|" + item.updatedAt() + "|" + item.artifactId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static EvidenceCursor decodeCursor(String cursor) {
        if (cursor == null) return null;
        if (cursor.isBlank() || cursor.length() > 256) throw invalidCursor();
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", -1);
            if (parts.length != 3 || parts[0].isBlank()) throw invalidCursor();
            return new EvidenceCursor(parts[0], Instant.parse(parts[1]), UUID.fromString(parts[2]));
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private static IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("cursor格式不合法");
    }

    /**
     * @program: CareerForge-AI
     * @description: ACTIVE个人证据分页结果
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param items 当前页轻量证据
     * @param nextCursor 下一页Cursor
     * @param hasMore 是否存在下一页
     */
    public record EvidencePage(
            List<PersonalEvidenceArtifactRepository.ActiveArtifactSummary> items,
            String nextCursor,
            boolean hasMore
    ) {
        public EvidencePage {
            items = List.copyOf(Objects.requireNonNull(items, "items不能为空"));
            if (hasMore != (nextCursor != null)) throw new IllegalArgumentException("分页状态不一致");
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 与证据类型绑定的分页位置
     * @author: Miao Zheng
     * @date: 2026-09-03
     * @param typeKey 证据类型过滤标识
     * @param beforeUpdatedAt 下一页更新时间上界
     * @param beforeArtifactId 同一更新时间下的证据ID上界
     */
    private record EvidenceCursor(String typeKey, Instant beforeUpdatedAt, UUID beforeArtifactId) {
        private EvidenceCursor {
            if (typeKey == null || typeKey.isBlank()
                    || beforeUpdatedAt == null || beforeArtifactId == null) throw invalidCursor();
        }
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