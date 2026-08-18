package com.leo.careerforgeai.career.api.dto.training;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提交当前用户选择的能力差距快照以生成训练计划草案
 * @author: Miao Zheng
 * @date: 2026-08-18
 * @param gapSnapshotId 当前用户选择的能力差距快照ID
 */
public record GenerateTrainingPlanRequest(
        @NotNull UUID gapSnapshotId
) {
}