package com.leo.careerforgeai.career.api.dto;

import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot.GapItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回固定岗位、画像和算法版本生成的不可变能力差距快照
 * @author: Miao Zheng
 * @date: 2026-08-17
 * @param snapshotId 能力差距快照ID
 * @param targetRoleId 输入目标岗位ID
 * @param targetRoleVersion 输入目标岗位版本
 * @param profileVersion 输入技能画像版本
 * @param algorithmVersion 匹配算法版本
 * @param items 差距明细
 * @param createdAt 快照生成时间
 */
public record SkillGapSnapshotResponse(
        UUID snapshotId,
        UUID targetRoleId,
        long targetRoleVersion,
        long profileVersion,
        String algorithmVersion,
        List<GapItem> items,
        Instant createdAt
) {
    public static SkillGapSnapshotResponse from(SkillGapSnapshot snapshot) {
        return new SkillGapSnapshotResponse(
                snapshot.snapshotId(),
                snapshot.targetRoleId(),
                snapshot.targetRoleVersion(),
                snapshot.profileVersion(),
                snapshot.algorithmVersion(),
                snapshot.items(),
                snapshot.createdAt()
        );
    }
}