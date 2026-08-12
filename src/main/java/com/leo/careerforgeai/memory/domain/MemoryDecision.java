package com.leo.careerforgeai.memory.domain;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 记录用户对Memory执行的显式决策及决策前后的状态
 * @author: Miao Zheng
 * @date: 2026-08-12
 * @param decisionId 服务端生成的决策审计ID
 * @param memoryId 被操作的Memory ID
 * @param ownerId Memory所属用户和执行决策的当前用户
 * @param decisionType 用户执行的确认、拒绝、替代或撤销动作
 * @param fromStatus 决策执行前状态
 * @param toStatus 决策执行后状态
 * @param expectedMemoryVersion 决策依据的Memory版本
 * @param replacementMemoryId 替代决策对应的新Memory ID
 * @param note 用户提供的可选决策说明
 * @param decidedAt 服务端记录的决策时间
 **/
public record MemoryDecision(
        UUID decisionId,
        UUID memoryId,
        ActorId ownerId,
        MemoryDecisionType decisionType,
        MemoryStatus fromStatus,
        MemoryStatus toStatus,
        long expectedMemoryVersion,
        UUID replacementMemoryId,
        String note,
        Instant decidedAt
) {

    public static final int MAX_NOTE_LENGTH = 500;

    public MemoryDecision {
        Objects.requireNonNull(decisionId, "decisionId 不能为空");
        Objects.requireNonNull(memoryId, "memoryId 不能为空");
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(decisionType, "decisionType 不能为空");
        Objects.requireNonNull(fromStatus, "fromStatus 不能为空");
        Objects.requireNonNull(toStatus, "toStatus 不能为空");
        Objects.requireNonNull(decidedAt, "decidedAt 不能为空");

        if (expectedMemoryVersion < 0) {
            throw new IllegalArgumentException(
                    "expectedMemoryVersion 不能小于0"
            );
        }

        MemoryStatus expectedTargetStatus =
                MemoryStateMachine.transition(
                        fromStatus,
                        decisionType
                );

        if (toStatus != expectedTargetStatus) {
            throw new IllegalArgumentException(
                    "toStatus 与决策类型不一致"
            );
        }

        if (decisionType == MemoryDecisionType.SUPERSEDE) {
            if (replacementMemoryId == null) {
                throw new IllegalArgumentException(
                        "替代决策必须提供replacementMemoryId"
                );
            }
            if (replacementMemoryId.equals(memoryId)) {
                throw new IllegalArgumentException(
                        "Memory不能被自身替代"
                );
            }
        } else if (replacementMemoryId != null) {
            throw new IllegalArgumentException(
                    "非替代决策不能提供replacementMemoryId"
            );
        }

        note = normalizeNote(note);
    }

    /**
     * 根据当前Memory和服务端Actor创建受控决策。
     */
    public static MemoryDecision create(
            UUID decisionId,
            MemoryItem memoryItem,
            ActorId currentActor,
            MemoryDecisionType decisionType,
            UUID replacementMemoryId,
            String note,
            Instant decidedAt
    ) {
        Objects.requireNonNull(memoryItem, "memoryItem 不能为空");
        Objects.requireNonNull(currentActor, "currentActor 不能为空");
        Objects.requireNonNull(decisionType, "decisionType 不能为空");
        Objects.requireNonNull(decidedAt, "decidedAt 不能为空");

        if (!memoryItem.ownerId().equals(currentActor)) {
            throw new IllegalArgumentException(
                    "当前Actor不能操作该Memory"
            );
        }
        if (decidedAt.isBefore(memoryItem.updatedAt())) {
            throw new IllegalArgumentException(
                    "决策时间不能早于Memory更新时间"
            );
        }

        MemoryStatus targetStatus = MemoryStateMachine.transition(
                memoryItem.status(),
                decisionType
        );

        return new MemoryDecision(
                decisionId,
                memoryItem.memoryId(),
                currentActor,
                decisionType,
                memoryItem.status(),
                targetStatus,
                memoryItem.version(),
                replacementMemoryId,
                note,
                decidedAt
        );
    }

    private static String normalizeNote(String note) {
        if (note == null) {
            return "";
        }

        String normalized = note.strip();

        if (normalized.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException(
                    "note 超过长度限制"
            );
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "note 不能包含控制字符"
            );
        }

        return normalized;
    }
}