package com.leo.careerforgeai.career.domain;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示基于固定目标岗位版本和用户画像版本计算出的不可变能力差距快照
 * @author: Miao Zheng
 * @date: 2026-08-12
 * @param snapshotId 差距快照UUID
 * @param ownerId 快照所属用户
 * @param targetRoleId 计算差距时使用的目标岗位ID
 * @param targetRoleVersion 计算差距时使用的目标岗位版本
 * @param profileVersion 计算差距时使用的用户画像版本，0表示空画像
 * @param items 经过Java校验的差距明细
 * @param createdAt 快照创建时间
 **/
public record SkillGapSnapshot(
        UUID snapshotId,
        ActorId ownerId,
        UUID targetRoleId,
        long targetRoleVersion,
        long profileVersion,
        List<GapItem> items,
        Instant createdAt
) {

    public static final int MAX_ITEMS = 100;

    public SkillGapSnapshot {
        Objects.requireNonNull(snapshotId, "snapshotId 不能为空");
        Objects.requireNonNull(ownerId, "ownerId 不能为空");
        Objects.requireNonNull(targetRoleId, "targetRoleId 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");

        if (targetRoleVersion < 1) {
            throw new IllegalArgumentException("targetRoleVersion必须从1开始");
        }
        if (profileVersion < 0) {
            throw new IllegalArgumentException("profileVersion不能小于0");
        }

        items = normalizeItems(items);
    }

    /**
     * 根据已经完成权限和版本校验的目标岗位与用户画像创建冻结快照。
     */
    public static SkillGapSnapshot create(
            UUID snapshotId,
            ActorId ownerId,
            UUID targetRoleId,
            long targetRoleVersion,
            long profileVersion,
            List<GapItem> items,
            Instant createdAt
    ) {
        return new SkillGapSnapshot(
                snapshotId,
                ownerId,
                targetRoleId,
                targetRoleVersion,
                profileVersion,
                items,
                createdAt
        );
    }

    private static List<GapItem> normalizeItems(List<GapItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items 不能为空");
        }
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("items 数量超过限制");
        }

        Set<UUID> itemIds = new HashSet<>();
        Set<String> requirementRefs = new HashSet<>();

        for (GapItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("items 不能包含空值");
            }
            if (!itemIds.add(item.gapItemId())) {
                throw new IllegalArgumentException("gapItemId 不能重复");
            }
            if (!requirementRefs.add(item.requirementRef())) {
                throw new IllegalArgumentException("requirementRef 不能重复");
            }
        }

        return List.copyOf(items);
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义岗位要求与用户已确认技能证据之间的匹配状态
     * @author: Miao Zheng
     * @date: 2026-08-12
     **/
    public enum GapStatus {

        /** 已有足够且可追溯的已确认证据。 */
        MATCHED,

        /** 已有相关证据，但深度、范围或熟练度不足。 */
        PARTIAL,

        /** 岗位存在该要求，但用户画像中没有相关证据。 */
        MISSING,

        /** 只有自述或弱来源，暂时不能确认已经掌握。 */
        UNVERIFIED
    }

    /**
     * @program: CareerForge-AI
     * @description: 表示一项岗位要求对应的能力差距及其画像证据
     * @author: Miao Zheng
     * @date: 2026-08-12
     * @param gapItemId 差距明细UUID，供训练计划稳定引用
     * @param requirementRef 指向目标岗位快照中具体要求的稳定引用
     * @param requirementText 岗位要求原文快照
     * @param status 差距判定状态
     * @param evidenceMemoryIds 支撑判定的已确认Memory ID
     * @param reason 经过校验的差距判定说明
     **/
    public record GapItem(
            UUID gapItemId,
            String requirementRef,
            String requirementText,
            GapStatus status,
            List<UUID> evidenceMemoryIds,
            String reason
    ) {

        public static final int MAX_REQUIREMENT_REF_LENGTH = 128;
        public static final int MAX_REQUIREMENT_TEXT_LENGTH = 1_000;
        public static final int MAX_REASON_LENGTH = 1_000;
        public static final int MAX_EVIDENCE_COUNT = 20;

        public GapItem {
            Objects.requireNonNull(gapItemId, "gapItemId 不能为空");
            Objects.requireNonNull(status, "status 不能为空");

            requirementRef = normalizeRequired(
                    requirementRef,
                    "requirementRef",
                    MAX_REQUIREMENT_REF_LENGTH
            );
            requirementText = normalizeRequired(
                    requirementText,
                    "requirementText",
                    MAX_REQUIREMENT_TEXT_LENGTH
            );
            reason = normalizeRequired(reason, "reason", MAX_REASON_LENGTH);
            evidenceMemoryIds = normalizeEvidenceMemoryIds(evidenceMemoryIds);

            if (status == GapStatus.MISSING && !evidenceMemoryIds.isEmpty()) {
                throw new IllegalArgumentException("MISSING差距不能包含证据Memory");
            }
            if (status != GapStatus.MISSING && evidenceMemoryIds.isEmpty()) {
                throw new IllegalArgumentException(status + "差距必须包含证据Memory");
            }
        }

        private static List<UUID> normalizeEvidenceMemoryIds(List<UUID> evidenceMemoryIds) {
            if (evidenceMemoryIds == null) {
                throw new IllegalArgumentException("evidenceMemoryIds 不能为空");
            }
            if (evidenceMemoryIds.size() > MAX_EVIDENCE_COUNT) {
                throw new IllegalArgumentException("evidenceMemoryIds 数量超过限制");
            }

            LinkedHashSet<UUID> normalized = new LinkedHashSet<>();

            for (UUID evidenceMemoryId : evidenceMemoryIds) {
                if (evidenceMemoryId == null) {
                    throw new IllegalArgumentException("evidenceMemoryIds 不能包含空值");
                }
                if (!normalized.add(evidenceMemoryId)) {
                    throw new IllegalArgumentException("evidenceMemoryIds 不能重复");
                }
            }

            return List.copyOf(normalized);
        }

        private static String normalizeRequired(String value, String fieldName, int maxLength) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " 不能为空");
            }

            String normalized = value.strip();

            if (normalized.length() > maxLength) {
                throw new IllegalArgumentException(fieldName + " 超过长度限制");
            }
            if (normalized.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException(fieldName + " 不能包含控制字符");
            }

            return normalized;
        }
    }
}