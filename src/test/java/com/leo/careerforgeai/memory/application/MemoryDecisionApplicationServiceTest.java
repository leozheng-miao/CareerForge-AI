package com.leo.careerforgeai.memory.application;

import com.leo.careerforgeai.memory.application.port.profile.MemoryDecisionRepository;
import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.application.profile.MemoryDecisionApplicationService;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecision;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecisionType;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemorySource;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryStatus;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.memory.domain.profile.TimeConstraintKey;
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
import com.leo.careerforgeai.memory.application.port.conversation.CoachingConversationRepository;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    private CoachingConversationRepository conversationRepository;
    private long nextTurnSequence;

    @BeforeEach
    void setUp() {
        actorProvider = new MutableCurrentActorProvider(ACTOR_A);
        conversationRepository = mock(CoachingConversationRepository.class);
        persistence = new InMemoryMemoryPersistence();
        nextTurnSequence = 1;

        service = new MemoryDecisionApplicationService(
                actorProvider,
                conversationRepository,
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
    void shouldConfirmReplacementAndReplayRepeatedRequest() {
        MemoryItem originalCandidate = pendingMemory(
                UUID.randomUUID(),
                ACTOR_A,
                null,
                "我每周可以学习10小时"
        );
        persistence.insert(originalCandidate);
        MemoryItem confirmedOriginal = service.confirm(
                originalCandidate.memoryId(),
                0,
                "确认原始时间"
        );

        MemoryItem replacementCandidate = pendingMemory(
                UUID.randomUUID(),
                ACTOR_A,
                confirmedOriginal.memoryId(),
                "我每周可以学习6小时"
        );
        persistence.insert(replacementCandidate);

        long expectedExistingVersion = confirmedOriginal.version();
        long expectedReplacementVersion = replacementCandidate.version();

        MemoryItem confirmedReplacement = service.confirmReplacement(
                confirmedOriginal.memoryId(),
                expectedExistingVersion,
                replacementCandidate.memoryId(),
                expectedReplacementVersion,
                "每周可用时间发生变化"
        );
        MemoryItem replayedReplacement = service.confirmReplacement(
                confirmedOriginal.memoryId(),
                expectedExistingVersion,
                replacementCandidate.memoryId(),
                expectedReplacementVersion,
                "重复提交不能覆盖原审计"
        );

        MemoryItem storedOriginal = persistence
                .findById(ACTOR_A, confirmedOriginal.memoryId())
                .orElseThrow();

        assertThat(confirmedReplacement.status()).isEqualTo(MemoryStatus.CONFIRMED);
        assertThat(confirmedReplacement.supersedesId()).isEqualTo(confirmedOriginal.memoryId());
        assertThat(storedOriginal.status()).isEqualTo(MemoryStatus.SUPERSEDED);
        assertThat(replayedReplacement).isEqualTo(confirmedReplacement);
        assertThat(persistence.findByMemoryId(ACTOR_A, replacementCandidate.memoryId()))
                .extracting(MemoryDecision::decisionType)
                .containsExactly(MemoryDecisionType.CONFIRM);
        assertThat(persistence.findByMemoryId(ACTOR_A, confirmedOriginal.memoryId()))
                .extracting(MemoryDecision::decisionType)
                .containsExactly(MemoryDecisionType.CONFIRM, MemoryDecisionType.SUPERSEDE);
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
        MemoryItem replayed = service.revoke(
                candidate.memoryId(),
                confirmed.version(),
                "重复撤销不能覆盖原审计"
        );

        assertThat(revoked.status()).isEqualTo(MemoryStatus.REVOKED);
        assertThat(replayed).isEqualTo(revoked);
        assertThat(persistence.findConfirmedByOwner(ACTOR_A)).isEmpty();
        assertThat(persistence.findByMemoryId(ACTOR_A, candidate.memoryId()))
                .extracting(MemoryDecision::decisionType)
                .containsExactly(MemoryDecisionType.CONFIRM, MemoryDecisionType.REVOKE);
    }

    @Test
    void shouldReplayRepeatedConfirmationWithoutAnotherDecision() {
        MemoryItem candidate = pendingMemory(
                UUID.randomUUID(),
                ACTOR_A,
                null,
                "我每周可以学习10小时"
        );
        persistence.insert(candidate);

        MemoryItem firstResult = service.confirm(candidate.memoryId(), 0, "用户确认");
        MemoryItem replayedResult = service.confirm(candidate.memoryId(), 0, "重复提交不会修改原审计");

        assertThat(replayedResult).isEqualTo(firstResult);
        assertThat(persistence.findByMemoryId(ACTOR_A, candidate.memoryId())).hasSize(1);
    }

    @Test
    void shouldRejectOwnedPendingMemoryAndReplayRepeatedRejection() {
        MemoryItem candidate = pendingMemory(
                UUID.randomUUID(),
                ACTOR_A,
                null,
                "我每周可以学习10小时"
        );
        persistence.insert(candidate);

        MemoryItem rejected = service.reject(candidate.memoryId(), 0, "用户拒绝");
        MemoryItem replayed = service.reject(candidate.memoryId(), 0, "重复拒绝");

        assertThat(rejected.status()).isEqualTo(MemoryStatus.REJECTED);
        assertThat(rejected.version()).isEqualTo(1);
        assertThat(replayed).isEqualTo(rejected);
        assertThat(persistence.findByMemoryId(ACTOR_A, candidate.memoryId())).hasSize(1);
    }

    @Test
    void shouldRejectConflictingDecisionAfterConfirmation() {
        MemoryItem candidate = pendingMemory(
                UUID.randomUUID(),
                ACTOR_A,
                null,
                "我每周可以学习10小时"
        );
        persistence.insert(candidate);
        service.confirm(candidate.memoryId(), 0, "用户确认");

        assertThatThrownBy(() -> service.reject(candidate.memoryId(), 0, "冲突拒绝"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Memory版本已经过期");

        assertThat(persistence.findByMemoryId(ACTOR_A, candidate.memoryId())).hasSize(1);
    }

    @Test
    void shouldRejectConfirmationWhenSourceTurnCannotBeRead() {
        MemoryItem candidate = pendingMemory(
                UUID.randomUUID(),
                ACTOR_A,
                null,
                "我每周可以学习10小时"
        );
        persistence.insert(candidate);

        UUID sourceTurnId = UUID.fromString(candidate.source().sourceId());
        when(conversationRepository.findTurn(ACTOR_A, sourceTurnId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(candidate.memoryId(), 0, "用户确认"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Memory来源Turn不存在或不属于当前用户");

        assertThat(persistence.findById(ACTOR_A, candidate.memoryId()))
                .get()
                .extracting(MemoryItem::status)
                .isEqualTo(MemoryStatus.PENDING);
        assertThat(persistence.findByMemoryId(ACTOR_A, candidate.memoryId())).isEmpty();
    }

    @Test
    void shouldRejectSilentConfirmationWhenSingleValueSlotAlreadyConfirmed() {
        MemoryItem existingCandidate = pendingMemory(
                UUID.randomUUID(),
                ACTOR_A,
                null,
                "我每周可以学习10小时"
        );
        persistence.insert(existingCandidate);
        MemoryItem confirmed = service.confirm(existingCandidate.memoryId(), 0, "确认原值");

        MemoryItem conflictingCandidate = pendingMemory(
                UUID.randomUUID(),
                ACTOR_A,
                null,
                "我每周可以学习6小时"
        );
        persistence.insert(conflictingCandidate);

        assertThatThrownBy(() -> service.confirm(
                conflictingCandidate.memoryId(),
                0,
                "不能静默覆盖"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("同一槽位已有CONFIRMED Memory，请使用显式替代");

        assertThat(persistence.findById(ACTOR_A, confirmed.memoryId()))
                .get()
                .extracting(MemoryItem::status)
                .isEqualTo(MemoryStatus.CONFIRMED);
        assertThat(persistence.findById(ACTOR_A, conflictingCandidate.memoryId()))
                .get()
                .extracting(MemoryItem::status)
                .isEqualTo(MemoryStatus.PENDING);
        assertThat(persistence.findByMemoryId(ACTOR_A, conflictingCandidate.memoryId())).isEmpty();
    }

    private MemoryItem pendingMemory(
            UUID memoryId,
            ActorId ownerId,
            UUID supersedesId,
            String content
    ) {
        ConversationTurn sourceTurn = completedUserTurn(ownerId, content);
        MemorySource source = new MemorySource(
                MemorySourceType.CONVERSATION_TURN,
                sourceTurn.turnId().toString(),
                sourceTurn.contentHash()
        );
        List<String> evidenceRefs = List.of(sourceTurn.turnId().toString());

        if (supersedesId == null) {
            return MemoryItem.createPending(
                    memoryId,
                    ownerId,
                    MemoryType.TIME_CONSTRAINT,
                    MemoryNormalizedKey.timeConstraint(TimeConstraintKey.WEEKLY_HOURS),
                    content,
                    source,
                    evidenceRefs,
                    NOW.minusSeconds(10)
            );
        }

        MemoryItem existingMemory = persistence.findById(ownerId, supersedesId).orElseThrow();
        return MemoryItem.createPendingReplacement(
                memoryId,
                existingMemory,
                content,
                source,
                evidenceRefs,
                NOW.minusSeconds(5)
        );
    }

    private ConversationTurn completedUserTurn(ActorId ownerId, String content) {
        ConversationTurn turn = ConversationTurn.completedUser(
                UUID.randomUUID(),
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.randomUUID(),
                ownerId,
                nextTurnSequence++,
                content,
                NOW.minusSeconds(20)
        );

        when(conversationRepository.findTurn(ownerId, turn.turnId()))
                .thenReturn(Optional.of(turn));
        return turn;
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
        public List<MemoryItem> findPendingByOwner(ActorId ownerId) {
            return memories.values().stream()
                    .filter(memory -> memory.ownerId().equals(ownerId))
                    .filter(memory -> memory.status() == MemoryStatus.PENDING)
                    .toList();
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