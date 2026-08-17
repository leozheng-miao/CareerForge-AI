package com.leo.careerforgeai.memory.application.profile;

import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryStatus;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 保存当前用户确定版本的已确认技能证据画像
 * @author: Miao Zheng
 * @date: 2026-08-16
 * @param ownerId 画像所属用户
 * @param profileVersion 已确认技能画像的有效状态变更版本
 * @param skillEvidence 当前版本包含的全部CONFIRMED技能证据
 */
public record ConfirmedSkillProfile(
        ActorId ownerId,
        long profileVersion,
        List<MemoryItem> skillEvidence
) {
    public ConfirmedSkillProfile {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        if (profileVersion < 0) {
            throw new IllegalArgumentException("profileVersion不能小于0");
        }
        if (skillEvidence == null) {
            throw new IllegalArgumentException("skillEvidence不能为空");
        }
        if (skillEvidence.stream().anyMatch(memory -> memory == null
                || !ownerId.equals(memory.ownerId())
                || memory.type() != MemoryType.SKILL_EVIDENCE
                || memory.status() != MemoryStatus.CONFIRMED)) {
            throw new IllegalStateException("技能画像包含非法owner、类型或状态");
        }
        if (profileVersion < skillEvidence.size()) {
            throw new IllegalStateException("profileVersion小于当前技能证据数量");
        }
        skillEvidence = List.copyOf(skillEvidence);
    }
}