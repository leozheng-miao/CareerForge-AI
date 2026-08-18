package com.leo.careerforgeai.career.api.dto.training;

import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.career.domain.TrainingPlanItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回可供用户审阅和执行的训练计划，不暴露内部生成审计信息
 * @author: Miao Zheng
 * @date: 2026-08-18
 * @param planId 训练计划ID
 * @param planVersion 当前用户训练计划业务版本
 * @param gapSnapshotId 来源能力差距快照ID
 * @param title 训练计划标题
 * @param status 计划状态
 * @param durationWeeks 计划周期周数
 * @param items 训练任务
 * @param version 乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param activatedAt 激活时间
 * @param completedAt 完成时间
 * @param cancelledAt 取消时间
 */
public record TrainingPlanResponse(
        UUID planId,
        long planVersion,
        UUID gapSnapshotId,
        String title,
        TrainingPlan.PlanStatus status,
        int durationWeeks,
        List<TrainingPlanItem> items,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt,
        Instant completedAt,
        Instant cancelledAt
) {
    public static TrainingPlanResponse from(TrainingPlan plan) {
        return new TrainingPlanResponse(
                plan.planId(),
                plan.planVersion(),
                plan.gapSnapshotId(),
                plan.title(),
                plan.status(),
                plan.durationWeeks(),
                plan.items(),
                plan.version(),
                plan.createdAt(),
                plan.updatedAt(),
                plan.activatedAt(),
                plan.completedAt(),
                plan.cancelledAt()
        );
    }
}