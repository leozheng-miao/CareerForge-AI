package com.leo.careerforgeai.memory.application.profile;

import com.leo.careerforgeai.memory.application.port.conversation.CoachingConversationRepository;
import com.leo.careerforgeai.memory.application.port.profile.MemoryDecisionRepository;
import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecision;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecisionType;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryStatus;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 在同一事务中执行Memory确认、拒绝、撤销和显式替代并记录审计
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MemoryDecisionApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final CoachingConversationRepository conversationRepository;
    private final MemoryRepository memoryRepository;
    private final MemoryDecisionRepository decisionRepository;
    private final Clock clock;

    public MemoryDecisionApplicationService(
            CurrentActorProvider currentActorProvider,
            CoachingConversationRepository conversationRepository,
            MemoryRepository memoryRepository,
            MemoryDecisionRepository decisionRepository,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.conversationRepository = Objects.requireNonNull(conversationRepository, "conversationRepository不能为空");
        this.memoryRepository = Objects.requireNonNull(memoryRepository, "memoryRepository不能为空");
        this.decisionRepository = Objects.requireNonNull(decisionRepository, "decisionRepository不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    /** 用户确认一条PENDING Memory，使其进入长期画像。 */
    @Transactional
    public MemoryItem confirm(UUID memoryId, long expectedVersion, String note) {
        return applySimpleDecision(memoryId, expectedVersion, MemoryDecisionType.CONFIRM, note);
    }

    /** 用户拒绝一条PENDING Memory，使其永不进入长期画像。 */
    @Transactional
    public MemoryItem reject(UUID memoryId, long expectedVersion, String note) {
        return applySimpleDecision(memoryId, expectedVersion, MemoryDecisionType.REJECT, note);
    }

    /** 用户撤销一条已经确认的Memory。 */
    @Transactional
    public MemoryItem revoke(UUID memoryId, long expectedVersion, String note) {
        return applySimpleDecision(memoryId, expectedVersion, MemoryDecisionType.REVOKE, note);
    }

    /**
     * 用户确认新候选并同时替代旧Memory。
     * 新旧Memory的更新和两条审计记录必须处于同一事务。
     */
    @Transactional
    public MemoryItem confirmReplacement(
            UUID existingMemoryId,
            long expectedExistingVersion,
            UUID replacementMemoryId,
            long expectedReplacementVersion,
            String note
    ) {
        if (Objects.equals(existingMemoryId, replacementMemoryId)) {
            throw new IllegalArgumentException("新旧Memory不能是同一条记录");
        }
        if (expectedExistingVersion < 0 || expectedReplacementVersion < 0) {
            throw new IllegalArgumentException("Memory预期版本不能小于0");
        }

        ActorId actorId = currentActorProvider.currentActor();
        MemoryItem existingMemory = requireOwnedMemory(actorId, existingMemoryId);
        MemoryItem replacementMemory = requireOwnedMemory(actorId, replacementMemoryId);

        if (existingMemory.version() != expectedExistingVersion
                || replacementMemory.version() != expectedReplacementVersion) {
            return replayReplacementOrThrow(
                    actorId,
                    existingMemory,
                    expectedExistingVersion,
                    replacementMemory,
                    expectedReplacementVersion
            );
        }

        requireValidReplacementPair(existingMemory, replacementMemory);
        requireTraceableConversationSource(actorId, replacementMemory);

        Instant decidedAt = clock.instant();
        MemoryDecision confirmReplacementDecision = MemoryDecision.create(
                UUID.randomUUID(),
                replacementMemory,
                actorId,
                MemoryDecisionType.CONFIRM,
                null,
                note,
                decidedAt
        );
        MemoryItem confirmedReplacement = replacementMemory.applyDecision(confirmReplacementDecision);
        updateOrThrow(actorId, confirmedReplacement, expectedReplacementVersion);
        decisionRepository.insert(confirmReplacementDecision);

        MemoryDecision supersedeExistingDecision = MemoryDecision.create(
                UUID.randomUUID(),
                existingMemory,
                actorId,
                MemoryDecisionType.SUPERSEDE,
                confirmedReplacement.memoryId(),
                note,
                decidedAt
        );
        MemoryItem supersededExisting = existingMemory.applyDecision(supersedeExistingDecision);
        updateOrThrow(actorId, supersededExisting, expectedExistingVersion);
        decisionRepository.insert(supersedeExistingDecision);
        return confirmedReplacement;
    }

    private MemoryItem applySimpleDecision(
            UUID memoryId,
            long expectedVersion,
            MemoryDecisionType decisionType,
            String note
    ) {
        ActorId actorId = currentActorProvider.currentActor();
        MemoryItem memoryItem = requireOwnedMemory(actorId, memoryId);

        if (memoryItem.version() != expectedVersion) {
            return replaySameDecisionOrThrow(actorId, memoryItem, expectedVersion, decisionType);
        }
        if (decisionType == MemoryDecisionType.CONFIRM) {
            requireConfirmableCandidate(actorId, memoryItem);
        }

        MemoryDecision decision = MemoryDecision.create(
                UUID.randomUUID(),
                memoryItem,
                actorId,
                decisionType,
                null,
                note,
                clock.instant()
        );

        MemoryItem updatedMemory = memoryItem.applyDecision(decision);
        updateOrThrow(actorId, updatedMemory, expectedVersion);
        decisionRepository.insert(decision);
        return updatedMemory;
    }

    private MemoryItem replayReplacementOrThrow(
            ActorId actorId,
            MemoryItem existingMemory,
            long expectedExistingVersion,
            MemoryItem replacementMemory,
            long expectedReplacementVersion
    ) {
        boolean completedReplacement =
                existingMemory.status() == MemoryStatus.SUPERSEDED
                        && replacementMemory.status() == MemoryStatus.CONFIRMED
                        && replacementMemory.type() == existingMemory.type()
                        && replacementMemory.normalizedKey().equals(existingMemory.normalizedKey())
                        && Objects.equals(replacementMemory.supersedesId(), existingMemory.memoryId());

        if (completedReplacement) {
            boolean replacementConfirmed = decisionRepository
                    .findByMemoryId(actorId, replacementMemory.memoryId())
                    .stream()
                    .anyMatch(decision ->
                            decision.decisionType() == MemoryDecisionType.CONFIRM
                                    && decision.expectedMemoryVersion() == expectedReplacementVersion);

            boolean existingSuperseded = decisionRepository
                    .findByMemoryId(actorId, existingMemory.memoryId())
                    .stream()
                    .anyMatch(decision ->
                            decision.decisionType() == MemoryDecisionType.SUPERSEDE
                                    && decision.expectedMemoryVersion() == expectedExistingVersion
                                    && replacementMemory.memoryId().equals(decision.replacementMemoryId()));

            if (replacementConfirmed && existingSuperseded) {
                return replacementMemory;
            }
        }

        throw new IllegalStateException("Memory替代版本已经过期");
    }

    private MemoryItem replaySameDecisionOrThrow(
            ActorId actorId,
            MemoryItem memoryItem,
            long expectedVersion,
            MemoryDecisionType decisionType
    ) {
        if (expectedVersion >= 0 && memoryItem.status() == decisionType.targetStatus()) {
            boolean sameDecisionExists = decisionRepository
                    .findByMemoryId(actorId, memoryItem.memoryId())
                    .stream()
                    .anyMatch(decision ->
                            decision.decisionType() == decisionType
                                    && decision.expectedMemoryVersion() == expectedVersion);

            if (sameDecisionExists) {
                return memoryItem;
            }
        }

        throw new IllegalStateException("Memory版本已经过期");
    }

    private void requireConfirmableCandidate(ActorId actorId, MemoryItem candidate) {
        if (candidate.supersedesId() != null) {
            throw new IllegalArgumentException("替代候选必须通过显式替代流程确认");
        }

        requireTraceableConversationSource(actorId, candidate);

        if (candidate.type() == MemoryType.SKILL_EVIDENCE) {
            return;
        }

        boolean confirmedConflict = memoryRepository
                .findByOwnerAndNormalizedKey(actorId, candidate.type(), candidate.normalizedKey())
                .stream()
                .anyMatch(existing ->
                        !existing.memoryId().equals(candidate.memoryId())
                                && existing.status() == MemoryStatus.CONFIRMED);

        if (confirmedConflict) {
            throw new IllegalStateException("同一槽位已有CONFIRMED Memory，请使用显式替代");
        }
    }

    private void requireTraceableConversationSource(ActorId actorId, MemoryItem memoryItem) {
        if (memoryItem.source().sourceType() != MemorySourceType.CONVERSATION_TURN) {
            throw new IllegalArgumentException("当前Memory来源类型尚不支持确认");
        }
        if (!memoryItem.evidenceRefs().contains(memoryItem.source().sourceId())) {
            throw new IllegalArgumentException("Memory主要来源不在证据列表中");
        }

        ConversationTurn sourceTurn = requireOwnedCompletedTurn(
                actorId,
                parseTurnId(memoryItem.source().sourceId())
        );

        if (!sourceTurn.contentHash().equals(memoryItem.source().sourceHash())) {
            throw new IllegalArgumentException("Memory来源内容已经发生变化");
        }
        if (!Objects.equals(sourceTurn.agentRunId(), memoryItem.sourceAgentRunId())) {
            throw new IllegalArgumentException("Memory来源Agent Run不一致");
        }

        for (String evidenceRef : memoryItem.evidenceRefs()) {
            ConversationTurn evidenceTurn = requireOwnedCompletedTurn(actorId, parseTurnId(evidenceRef));
            if (!evidenceTurn.sessionId().equals(sourceTurn.sessionId())) {
                throw new IllegalArgumentException("Memory证据Turn不属于同一Session");
            }
        }
    }

    private ConversationTurn requireOwnedCompletedTurn(ActorId actorId, UUID turnId) {
        ConversationTurn turn = conversationRepository.findTurn(actorId, turnId)
                .orElseThrow(() -> new IllegalArgumentException("Memory来源Turn不存在或不属于当前用户"));

        if (!turn.isEligibleForMemoryExtraction()) {
            throw new IllegalArgumentException("Memory来源Turn不是COMPLETED状态");
        }
        return turn;
    }

    private UUID parseTurnId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Memory来源Turn ID格式不合法", exception);
        }
    }

    private MemoryItem requireOwnedMemory(ActorId actorId, UUID memoryId) {
        Objects.requireNonNull(actorId, "actorId不能为空");
        Objects.requireNonNull(memoryId, "memoryId不能为空");

        return memoryRepository.findById(actorId, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory不存在或不属于当前用户"));
    }

    private static void requireExpectedVersion(MemoryItem memoryItem, long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion不能小于0");
        }
        if (memoryItem.version() != expectedVersion) {
            throw new IllegalStateException("Memory版本已经过期");
        }
    }

    private static void requireValidReplacementPair(
            MemoryItem existingMemory,
            MemoryItem replacementMemory
    ) {
        if (existingMemory.status() != MemoryStatus.CONFIRMED) {
            throw new IllegalArgumentException("只能替代CONFIRMED Memory");
        }
        if (replacementMemory.status() != MemoryStatus.PENDING) {
            throw new IllegalArgumentException("替代候选必须是PENDING状态");
        }
        if (!Objects.equals(replacementMemory.supersedesId(), existingMemory.memoryId())) {
            throw new IllegalArgumentException("替代候选没有引用当前旧Memory");
        }
        if (replacementMemory.type() != existingMemory.type()) {
            throw new IllegalArgumentException("新旧Memory类型不一致");
        }
        if (!replacementMemory.normalizedKey().equals(existingMemory.normalizedKey())) {
            throw new IllegalArgumentException("新旧Memory不属于同一冲突槽位");
        }
    }

    private void updateOrThrow(ActorId actorId, MemoryItem updatedMemory, long expectedVersion) {
        boolean updated = memoryRepository.updateIfVersionMatches(actorId, updatedMemory, expectedVersion);
        if (!updated) {
            throw new IllegalStateException("Memory并发更新冲突");
        }
    }
}