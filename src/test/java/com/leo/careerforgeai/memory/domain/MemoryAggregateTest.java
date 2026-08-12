package com.leo.careerforgeai.memory.domain;

import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Memory候选创建、用户确认、owner隔离、乐观锁和替代流程
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
class MemoryAggregateTest {

    private static final ActorId ACTOR_A = new ActorId("actor-a");
    private static final ActorId ACTOR_B = new ActorId("actor-b");

    private static final UUID MEMORY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID REPLACEMENT_MEMORY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-12T01:00:00Z");

    @Test
    void shouldCreateOnlyPendingMemoryCandidate() {
        MemoryItem candidate = pendingMemory();

        assertThat(candidate.ownerId()).isEqualTo(ACTOR_A);
        assertThat(candidate.status()).isEqualTo(MemoryStatus.PENDING);
        assertThat(candidate.version()).isZero();
        assertThat(candidate.content()).isEqualTo("我每周可以学习10小时");
        assertThat(candidate.contentHash()).matches("[0-9a-f]{64}");
        assertThat(candidate.evidenceRefs())
                .containsExactly("turn-user-1");
    }

    @Test
    void shouldConfirmPendingMemoryAndIncreaseVersion() {
        MemoryItem candidate = pendingMemory();

        MemoryDecision decision = MemoryDecision.create(
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000011"
                ),
                candidate,
                ACTOR_A,
                MemoryDecisionType.CONFIRM,
                null,
                "用户确认时间投入准确",
                CREATED_AT.plusSeconds(10)
        );

        MemoryItem confirmed = candidate.applyDecision(decision);

        assertThat(decision.fromStatus())
                .isEqualTo(MemoryStatus.PENDING);
        assertThat(decision.toStatus())
                .isEqualTo(MemoryStatus.CONFIRMED);

        assertThat(confirmed.status())
                .isEqualTo(MemoryStatus.CONFIRMED);
        assertThat(confirmed.version()).isEqualTo(1);
        assertThat(confirmed.content())
                .isEqualTo(candidate.content());
        assertThat(confirmed.contentHash())
                .isEqualTo(candidate.contentHash());
    }

    @Test
    void shouldRejectDecisionFromAnotherActor() {
        MemoryItem candidate = pendingMemory();

        assertThatThrownBy(() -> MemoryDecision.create(
                UUID.randomUUID(),
                candidate,
                ACTOR_B,
                MemoryDecisionType.CONFIRM,
                null,
                null,
                CREATED_AT.plusSeconds(10)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Actor");
    }

    @Test
    void shouldRejectRepeatedDecisionUsingStaleVersion() {
        MemoryItem candidate = pendingMemory();

        MemoryDecision firstDecision = MemoryDecision.create(
                UUID.randomUUID(),
                candidate,
                ACTOR_A,
                MemoryDecisionType.CONFIRM,
                null,
                null,
                CREATED_AT.plusSeconds(10)
        );

        MemoryItem confirmed =
                candidate.applyDecision(firstDecision);

        assertThatThrownBy(() ->
                confirmed.applyDecision(firstDecision)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("过期Memory版本");
    }

    @Test
    void shouldExplicitlyReplaceConfirmedMemory() {
        MemoryItem originalCandidate = pendingMemory();

        MemoryDecision confirmOriginalDecision =
                MemoryDecision.create(
                        UUID.randomUUID(),
                        originalCandidate,
                        ACTOR_A,
                        MemoryDecisionType.CONFIRM,
                        null,
                        null,
                        CREATED_AT.plusSeconds(10)
                );

        MemoryItem confirmedOriginal =
                originalCandidate.applyDecision(
                        confirmOriginalDecision
                );

        MemoryItem replacementCandidate =
                MemoryItem.createPendingReplacement(
                        REPLACEMENT_MEMORY_ID,
                        confirmedOriginal,
                        "我每周可以学习6小时",
                        source("turn-user-2", "2"),
                        List.of("turn-user-2"),
                        CREATED_AT.plusSeconds(20)
                );

        MemoryDecision confirmReplacementDecision =
                MemoryDecision.create(
                        UUID.randomUUID(),
                        replacementCandidate,
                        ACTOR_A,
                        MemoryDecisionType.CONFIRM,
                        null,
                        null,
                        CREATED_AT.plusSeconds(30)
                );

        MemoryItem confirmedReplacement =
                replacementCandidate.applyDecision(
                        confirmReplacementDecision
                );

        MemoryDecision supersedeOldDecision =
                MemoryDecision.create(
                        UUID.randomUUID(),
                        confirmedOriginal,
                        ACTOR_A,
                        MemoryDecisionType.SUPERSEDE,
                        confirmedReplacement.memoryId(),
                        "每周可用时间发生变化",
                        CREATED_AT.plusSeconds(30)
                );

        MemoryItem supersededOriginal =
                confirmedOriginal.applyDecision(
                        supersedeOldDecision
                );

        assertThat(confirmedReplacement.status())
                .isEqualTo(MemoryStatus.CONFIRMED);
        assertThat(confirmedReplacement.supersedesId())
                .isEqualTo(confirmedOriginal.memoryId());

        assertThat(supersededOriginal.status())
                .isEqualTo(MemoryStatus.SUPERSEDED);
        assertThat(supersedeOldDecision.replacementMemoryId())
                .isEqualTo(confirmedReplacement.memoryId());
    }

    @Test
    void shouldRejectMismatchedNormalizedKey() {
        assertThatThrownBy(() -> MemoryItem.createPending(
                MEMORY_ID,
                ACTOR_A,
                MemoryType.CAREER_GOAL,
                MemoryNormalizedKey.timeConstraint(
                        TimeConstraintKey.WEEKLY_HOURS
                ),
                "目标成为AI Agent开发工程师",
                source("turn-user-1", "1"),
                List.of("turn-user-1"),
                CREATED_AT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Memory类型不匹配");
    }

    private MemoryItem pendingMemory() {
        return MemoryItem.createPending(
                MEMORY_ID,
                ACTOR_A,
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(
                        TimeConstraintKey.WEEKLY_HOURS
                ),
                "  我每周可以学习10小时  ",
                source("turn-user-1", "1"),
                List.of("turn-user-1"),
                CREATED_AT
        );
    }

    private MemorySource source(
            String sourceId,
            String hashCharacter
    ) {
        return new MemorySource(
                MemorySourceType.CONVERSATION_TURN,
                sourceId,
                hashCharacter.repeat(64)
        );
    }
}