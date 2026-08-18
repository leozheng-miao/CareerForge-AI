package com.leo.careerforgeai.career.api.dto.training;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * @program: CareerForge-AI
 * @description: 定义训练计划状态操作必须提交的乐观锁版本
 * @author: Miao Zheng
 * @date: 2026-08-18
 * @param expectedVersion 用户操作前读取到的训练计划版本
 */
public record TrainingPlanVersionRequest(
        @NotNull @PositiveOrZero Long expectedVersion
) {
}