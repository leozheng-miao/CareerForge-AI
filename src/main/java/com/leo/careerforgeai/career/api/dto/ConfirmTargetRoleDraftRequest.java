package com.leo.careerforgeai.career.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * @program: CareerForge-AI
 * @description: 定义用户确认目标岗位草案时必须提交的版本前置条件
 * @author: Miao Zheng
 * @date: 2026-08-16
 * @param expectedVersion 用户审阅草案时看到的乐观锁版本
 */
public record ConfirmTargetRoleDraftRequest(
        @NotNull
        @PositiveOrZero
        Long expectedVersion
) {
}