package com.leo.careerforgeai.interview.application.evidence;

import com.leo.careerforgeai.interview.application.port.PersonalEvidenceArtifactRepository;
import com.leo.careerforgeai.interview.domain.PersonalEvidenceArtifact;
import com.leo.careerforgeai.interview.domain.PersonalEvidenceStatus;
import com.leo.careerforgeai.interview.domain.PersonalEvidenceType;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证个人证据完整生命周期、owner隔离、版本冲突和确定性Unicode切片
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
class PersonalEvidenceApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");
    private static final ActorId OWNER_A = new ActorId("owner-a");
    private static final ActorId OWNER_B = new ActorId("owner-b");

    @Test
    void shouldCreateUpdateRejectStaleVersionAndRevokeArtifact() {
        AtomicReference<ActorId> actor = new AtomicReference<>(OWNER_A);
        InMemoryPersonalEvidenceRepository repository = new InMemoryPersonalEvidenceRepository();
        PersonalEvidenceApplicationService service = service(actor, repository);

        PersonalEvidenceArtifact created = service.create(
                PersonalEvidenceType.RESUME,
                "resume.md",
                "# 简历\r\n\r\nJava 21与Spring Boot项目经验"
        );

        assertThat(created.artifactVersion()).isEqualTo(1);
        assertThat(created.status()).isEqualTo(PersonalEvidenceStatus.ACTIVE);
        assertThat(created.content()).isEqualTo("# 简历\n\nJava 21与Spring Boot项目经验");
        assertThat(created.sourceHash()).matches("[0-9a-f]{64}");
        assertThat(created.chunks()).isNotEmpty();

        PersonalEvidenceArtifact updated = service.update(
                created.artifactId(),
                1,
                "resume-v2.md",
                "# 简历\n\nJava 21、Spring Boot与LangGraph4j项目经验"
        );

        assertThat(updated.artifactVersion()).isEqualTo(2);
        assertThat(updated.status()).isEqualTo(PersonalEvidenceStatus.ACTIVE);
        assertThat(repository.findVersion(OWNER_A, created.artifactId(), 1))
                .get()
                .satisfies(previous -> {
                    assertThat(previous.status()).isEqualTo(PersonalEvidenceStatus.SUPERSEDED);
                    assertThat(previous.supersededByVersion()).isEqualTo(2);
                });

        assertThatThrownBy(() -> service.update(
                created.artifactId(),
                1,
                "stale.md",
                "过期客户端正文"
        )).isInstanceOf(PersonalEvidenceVersionConflictException.class);

        assertThat(service.get(created.artifactId()).artifactVersion()).isEqualTo(2);

        PersonalEvidenceArtifact revoked = service.revoke(created.artifactId(), 2);
        assertThat(revoked.status()).isEqualTo(PersonalEvidenceStatus.REVOKED);

        assertThatThrownBy(() -> service.get(created.artifactId()))
                .isInstanceOf(PersonalEvidenceNotFoundException.class);

        assertThat(service.getVersion(created.artifactId(), 2).status())
                .isEqualTo(PersonalEvidenceStatus.REVOKED);
    }

    @Test
    void shouldHideArtifactFromAnotherOwner() {
        AtomicReference<ActorId> actor = new AtomicReference<>(OWNER_A);
        InMemoryPersonalEvidenceRepository repository = new InMemoryPersonalEvidenceRepository();
        PersonalEvidenceApplicationService service = service(actor, repository);

        PersonalEvidenceArtifact artifact = service.create(
                PersonalEvidenceType.PROJECT,
                "careerforge.md",
                "CareerForge-AI项目证据"
        );

        actor.set(OWNER_B);

        assertThatThrownBy(() -> service.get(artifact.artifactId()))
                .isInstanceOf(PersonalEvidenceNotFoundException.class);
        assertThat(repository.findVersion(OWNER_B, artifact.artifactId(), 1)).isEmpty();
    }

    @Test
    void shouldGenerateStableHashesAndUnicodeCodePointChunks() {
        PersonalEvidenceArtifactFactory factory = new PersonalEvidenceArtifactFactory();
        UUID artifactId = UUID.randomUUID();
        String body = "Java🙂 Spring Boot项目经验。".repeat(300);
        String rawContent = "\uFEFF# 项目\r\n\r\n" + body;
        String normalizedContent = "# 项目\n\n" + body;

        PersonalEvidenceArtifact first = factory.create(
                artifactId,
                1,
                OWNER_A,
                PersonalEvidenceType.PROJECT,
                "project.md",
                rawContent,
                NOW
        );
        PersonalEvidenceArtifact second = factory.create(
                artifactId,
                1,
                OWNER_A,
                PersonalEvidenceType.PROJECT,
                "project.md",
                normalizedContent,
                NOW.plusSeconds(10)
        );

        assertThat(first.content()).isEqualTo(normalizedContent);
        assertThat(first.sourceHash()).isEqualTo(second.sourceHash());
        assertThat(first.chunks()).hasSizeGreaterThan(1);
        assertThat(first.chunks().stream()
                .map(PersonalEvidenceArtifact.Chunk::evidenceChunkId)
                .toList()).isEqualTo(second.chunks().stream()
                .map(PersonalEvidenceArtifact.Chunk::evidenceChunkId)
                .toList());

        first.chunks().forEach(chunk -> assertThat(
                chunk.chunkContent().codePointCount(0, chunk.chunkContent().length())
        ).isEqualTo(chunk.endOffset() - chunk.startOffset()));
    }

    private PersonalEvidenceApplicationService service(
            AtomicReference<ActorId> actor,
            InMemoryPersonalEvidenceRepository repository
    ) {
        return new PersonalEvidenceApplicationService(
                actor::get,
                repository,
                new PersonalEvidenceArtifactFactory(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    /**
     * @program: CareerForge-AI
     * @description: 在内存中模拟个人证据版本、ACTIVE查询和生命周期CAS
     * @author: Miao Zheng
     * @date: 2026-08-27
     **/
    private static final class InMemoryPersonalEvidenceRepository
            implements PersonalEvidenceArtifactRepository {

        private final Map<String, PersonalEvidenceArtifact> artifacts = new HashMap<>();

        @Override
        public PersonalEvidenceArtifact create(PersonalEvidenceArtifact artifact) {
            if (artifacts.putIfAbsent(key(artifact), artifact) != null) {
                throw new IllegalStateException("个人证据版本已经存在");
            }
            return artifact;
        }

        @Override
        public Optional<PersonalEvidenceArtifact> findActive(ActorId ownerId, UUID artifactId) {
            return artifacts.values().stream()
                    .filter(artifact -> artifact.ownerId().equals(ownerId))
                    .filter(artifact -> artifact.artifactId().equals(artifactId))
                    .filter(artifact -> artifact.status() == PersonalEvidenceStatus.ACTIVE)
                    .max(Comparator.comparingLong(PersonalEvidenceArtifact::artifactVersion));
        }

        @Override
        public Optional<PersonalEvidenceArtifact> findVersion(
                ActorId ownerId,
                UUID artifactId,
                long artifactVersion
        ) {
            return Optional.ofNullable(artifacts.get(key(ownerId, artifactId, artifactVersion)));
        }

        @Override
        public boolean replaceActiveIfVersionMatches(
                ActorId ownerId,
                PersonalEvidenceArtifact supersededArtifact,
                PersonalEvidenceArtifact replacement,
                long expectedVersion
        ) {
            Optional<PersonalEvidenceArtifact> active = findActive(ownerId, supersededArtifact.artifactId());
            if (active.isEmpty() || active.get().artifactVersion() != expectedVersion) return false;
            if (artifacts.containsKey(key(replacement))) return false;

            artifacts.put(key(supersededArtifact), supersededArtifact);
            artifacts.put(key(replacement), replacement);
            return true;
        }

        @Override
        public boolean revokeActiveIfVersionMatches(
                ActorId ownerId,
                PersonalEvidenceArtifact revokedArtifact,
                long expectedVersion
        ) {
            Optional<PersonalEvidenceArtifact> active = findActive(ownerId, revokedArtifact.artifactId());
            if (active.isEmpty() || active.get().artifactVersion() != expectedVersion) return false;

            artifacts.put(key(revokedArtifact), revokedArtifact);
            return true;
        }

        @Override
        public Optional<PersonalEvidenceArtifact> findVersionForSnapshot(
                ActorId ownerId,
                UUID artifactId,
                long artifactVersion
        ) {
            return findVersion(ownerId, artifactId, artifactVersion);
        }

        private static String key(PersonalEvidenceArtifact artifact) {
            return key(artifact.ownerId(), artifact.artifactId(), artifact.artifactVersion());
        }

        private static String key(ActorId ownerId, UUID artifactId, long artifactVersion) {
            return ownerId.value() + ":" + artifactId + ":" + artifactVersion;
        }
    }
}