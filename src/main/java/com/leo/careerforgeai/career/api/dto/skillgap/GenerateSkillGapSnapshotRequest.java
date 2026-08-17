package com.leo.careerforgeai.career.api.dto.skillgap;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提交需要生成能力差距快照的固定岗位和画像版本
 * @author: Miao Zheng
 * @date: 2026-08-17
 * @param targetRoleId 当前用户已经确认的目标岗位ID
 * @param expectedTargetRoleVersion 客户端读取到的目标岗位版本
 * @param expectedProfileVersion 客户端读取到的技能画像版本
 */
public record GenerateSkillGapSnapshotRequest(
        @NotNull UUID targetRoleId,
        @Min(1) long expectedTargetRoleVersion,
        @PositiveOrZero long expectedProfileVersion
) {
}