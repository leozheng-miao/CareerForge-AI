package com.leo.careerforgeai.memory.application;

import com.leo.careerforgeai.memory.application.port.MemoryDecisionRepository;
import com.leo.careerforgeai.memory.application.port.MemoryRepository;
import com.leo.careerforgeai.memory.domain.MemoryDecision;
import com.leo.careerforgeai.memory.domain.MemoryDecisionType;
import com.leo.careerforgeai.memory.domain.MemoryItem;
import com.leo.careerforgeai.memory.domain.MemoryStatus;
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
    private final MemoryRepository memoryRepository;
    private final MemoryDecisionRepository decisionRepository;
    private final Clock clock;

    public MemoryDecisionApplicationService(
            CurrentActorProvider currentActorProvider,
            MemoryRepository memoryRepository,
            MemoryDecisionRepository decisionRepository,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider 不能为空");
        this.memoryRepository = Objects.requireNonNull(memoryRepository, "memoryRepository 不能为空");
        this.decisionRepository = Objects.requireNonNull(decisionRepository, "decisionRepository 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
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

        ActorId actorId = currentActorProvider.currentActor();
        MemoryItem existingMemory = requireOwnedMemory(actorId, existingMemoryId);
        MemoryItem replacementMemory = requireOwnedMemory(actorId, replacementMemoryId);

        requireExpectedVersion(existingMemory, expectedExistingVersion);
        requireExpectedVersion(replacementMemory, expectedReplacementVersion);
        requireValidReplacementPair(existingMemory, replacementMemory);

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

        requireExpectedVersion(memoryItem, expectedVersion);

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

    private MemoryItem requireOwnedMemory(ActorId actorId, UUID memoryId) {
        Objects.requireNonNull(actorId, "actorId 不能为空");
        Objects.requireNonNull(memoryId, "memoryId 不能为空");

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