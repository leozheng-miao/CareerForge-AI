package com.leo.careerforgeai.memory.application;

import com.leo.careerforgeai.memory.application.port.MemoryDecisionRepository;
import com.leo.careerforgeai.memory.application.port.MemoryRepository;
import com.leo.careerforgeai.memory.domain.MemoryDecision;
import com.leo.careerforgeai.memory.domain.MemoryItem;
import com.leo.careerforgeai.memory.domain.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.MemorySource;
import com.leo.careerforgeai.memory.domain.MemorySourceType;
import com.leo.careerforgeai.memory.domain.MemoryStatus;
import com.leo.careerforgeai.memory.domain.MemoryType;
import com.leo.careerforgeai.memory.domain.TimeConstraintKey;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.testsupport.MutableCurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 使用内存持久化Fake验证Memory决策编排、owner隔离、版本校验和替代流程
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
class MemoryDecisionApplicationServiceTest {

    private static final ActorId ACTOR_A = new ActorId("actor-a");
    private static final ActorId ACTOR_B = new ActorId("actor-b");
    private static final Instant NOW = Instant.parse("2026-08-12T02:00:00Z");

    private MutableCurrentActorProvider actorProvider;
    private InMemoryMemoryPersistence persistence;
    private MemoryDecisionApplicationService service;

    @BeforeEach
    void setUp() {
        actorProvider = new MutableCurrentActorProvider(ACTOR_A);
        persistence = new InMemoryMemoryPersistence();
        service = new MemoryDecisionApplicationService(
                actorProvider,
                persistence,
                persistence,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldConfirmOwnedPendingMemoryAndRecordDecision() {
        MemoryItem candidate = pendingMemory(UUID.randomUUID(), ACTOR_A, null, "我每周可以学习10小时");
        persistence.insert(candidate);

        MemoryItem confirmed = service.confirm(candidate.memoryId(), 0, "用户确认");

        assertThat(confirmed.status()).isEqualTo(MemoryStatus.CONFIRMED);
        assertThat(confirmed.version()).isEqualTo(1);
        assertThat(persistence.findById(ACTOR_A, candidate.memoryId()))
                .contains(confirmed);

        List<MemoryDecision> decisions = persistence.findByMemoryId(ACTOR_A, candidate.memoryId());
        assertThat(decisions).hasSize(1);
        assertThat(decisions.getFirst().toStatus()).isEqualTo(MemoryStatus.CONFIRMED);
    }

    @Test
    void shouldHideMemoryFromAnotherActor() {
        MemoryItem candidate = pendingMemory(UUID.randomUUID(), ACTOR_A, null, "我每周可以学习10小时");
        persistence.insert(candidate);
        actorProvider.switchTo(ACTOR_B);

        assertThatThrownBy(() -> service.confirm(candidate.memoryId(), 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Memory不存在或不属于当前用户");

        assertThat(persistence.findById(ACTOR_A, candidate.memoryId()))
                .get()
                .extracting(MemoryItem::status)
                .isEqualTo(MemoryStatus.PENDING);
    }

    @Test
    void shouldRejectStaleVersionBeforeUpdatingMemory() {
        MemoryItem candidate = pendingMemory(UUID.randomUUID(), ACTOR_A, null, "我每周可以学习10小时");
        persistence.insert(candidate);

        assertThatThrownBy(() -> service.confirm(candidate.memoryId(), 1, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Memory版本已经过期");

        assertThat(persistence.findByMemoryId(ACTOR_A, candidate.memoryId())).isEmpty();
    }

    @Test
    void shouldConfirmReplacementAndSupersedeOldMemory() {
        MemoryItem originalCandidate = pendingMemory(UUID.randomUUID(), ACTOR_A, null, "我每周可以学习10小时");
        persistence.insert(originalCandidate);

        MemoryItem confirmedOriginal = service.confirm(originalCandidate.memoryId(), 0, "确认原始时间");
        MemoryItem replacementCandidate = pendingMemory(
                UUID.randomUUID(),
                ACTOR_A,
                confirmedOriginal.memoryId(),
                "我每周可以学习6小时"
        );
        persistence.insert(replacementCandidate);

        MemoryItem confirmedReplacement = service.confirmReplacement(
                confirmedOriginal.memoryId(),
                confirmedOriginal.version(),
                replacementCandidate.memoryId(),
                replacementCandidate.version(),
                "每周可用时间发生变化"
        );

        MemoryItem storedOriginal = persistence.findById(ACTOR_A, confirmedOriginal.memoryId()).orElseThrow();

        assertThat(confirmedReplacement.status()).isEqualTo(MemoryStatus.CONFIRMED);
        assertThat(storedOriginal.status()).isEqualTo(MemoryStatus.SUPERSEDED);
        assertThat(persistence.findByMemoryId(ACTOR_A, replacementCandidate.memoryId())).hasSize(1);
        assertThat(persistence.findByMemoryId(ACTOR_A, confirmedOriginal.memoryId())).hasSize(2);
    }

    @Test
    void shouldExcludeRevokedMemoryFromEffectiveProfileRead() {
        MemoryItem candidate = pendingMemory(
                UUID.randomUUID(),
                ACTOR_A,
                null,
                "我每周可以学习10小时"
        );
        persistence.insert(candidate);

        MemoryItem confirmed = service.confirm(candidate.memoryId(), 0, "用户确认");

        assertThat(persistence.findConfirmedByOwner(ACTOR_A))
                .containsExactly(confirmed);

        MemoryItem revoked = service.revoke(
                candidate.memoryId(),
                confirmed.version(),
                "用户撤销该Memory"
        );

        assertThat(revoked.status()).isEqualTo(MemoryStatus.REVOKED);
        assertThat(persistence.findConfirmedByOwner(ACTOR_A)).isEmpty();
    }

    private MemoryItem pendingMemory(UUID memoryId, ActorId ownerId, UUID supersedesId, String content) {
        if (supersedesId == null) {
            return MemoryItem.createPending(
                    memoryId,
                    ownerId,
                    MemoryType.TIME_CONSTRAINT,
                    MemoryNormalizedKey.timeConstraint(TimeConstraintKey.WEEKLY_HOURS),
                    content,
                    source(memoryId),
                    List.of("turn-user-1"),
                    NOW.minusSeconds(10)
            );
        }

        MemoryItem existingMemory = persistence.findById(ownerId, supersedesId).orElseThrow();

        return MemoryItem.createPendingReplacement(
                memoryId,
                existingMemory,
                content,
                source(memoryId),
                List.of("turn-user-2"),
                NOW.minusSeconds(5)
        );
    }

    private MemorySource source(UUID memoryId) {
        String sourceHash = memoryId.toString().replace("-", "");
        sourceHash = (sourceHash + sourceHash).substring(0, 64);

        return new MemorySource(
                MemorySourceType.CONVERSATION_TURN,
                "turn-" + memoryId,
                sourceHash
        );
    }

    /**
     * 同时实现两个Repository端口的测试内存存储，仅验证应用编排，不模拟MySQL事务。
     */
    private static final class InMemoryMemoryPersistence
            implements MemoryRepository, MemoryDecisionRepository {

        private final Map<OwnedMemoryKey, MemoryItem> memories = new HashMap<>();
        private final List<MemoryDecision> decisions = new ArrayList<>();

        @Override
        public void insert(MemoryItem memoryItem) {
            OwnedMemoryKey key = new OwnedMemoryKey(memoryItem.ownerId(), memoryItem.memoryId());

            if (memories.putIfAbsent(key, memoryItem) != null) {
                throw new IllegalStateException("Memory已经存在");
            }
        }

        @Override
        public Optional<MemoryItem> findById(ActorId ownerId, UUID memoryId) {
            return Optional.ofNullable(memories.get(new OwnedMemoryKey(ownerId, memoryId)));
        }

        @Override
        public List<MemoryItem> findConfirmedByOwner(ActorId ownerId) {
            return memories.values().stream()
                    .filter(memory -> memory.ownerId().equals(ownerId))
                    .filter(memory -> memory.status() == MemoryStatus.CONFIRMED)
                    .toList();
        }

        @Override
        public List<MemoryItem> findByOwnerAndNormalizedKey(
                ActorId ownerId,
                MemoryType type,
                MemoryNormalizedKey normalizedKey
        ) {
            return memories.values().stream()
                    .filter(memory -> memory.ownerId().equals(ownerId))
                    .filter(memory -> memory.type() == type)
                    .filter(memory -> memory.normalizedKey().equals(normalizedKey))
                    .toList();
        }

        @Override
        public boolean updateIfVersionMatches(
                ActorId ownerId,
                MemoryItem updatedMemory,
                long expectedVersion
        ) {
            OwnedMemoryKey key = new OwnedMemoryKey(ownerId, updatedMemory.memoryId());
            MemoryItem currentMemory = memories.get(key);

            if (currentMemory == null || currentMemory.version() != expectedVersion) {
                return false;
            }

            memories.put(key, updatedMemory);
            return true;
        }

        @Override
        public void insert(MemoryDecision decision) {
            decisions.add(decision);
        }

        @Override
        public List<MemoryDecision> findByMemoryId(ActorId ownerId, UUID memoryId) {
            return decisions.stream()
                    .filter(decision -> decision.ownerId().equals(ownerId))
                    .filter(decision -> decision.memoryId().equals(memoryId))
                    .toList();
        }

        private record OwnedMemoryKey(ActorId ownerId, UUID memoryId) {
        }
    }
}