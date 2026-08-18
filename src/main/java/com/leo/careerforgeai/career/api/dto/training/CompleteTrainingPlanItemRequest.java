package com.leo.careerforgeai.career.api.dto.training;

import com.leo.careerforgeai.career.domain.TrainingPlanItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 提交训练计划项完成操作的版本前置条件和用户证据引用
 * @author: Miao Zheng
 * @date: 2026-08-18
 * @param expectedVersion 用户操作前读取到的训练计划版本
 * @param evidenceRefs 用户提交的代码、报告、截图或演示证据引用
 */
public record CompleteTrainingPlanItemRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotNull @Size(min = 1, max = TrainingPlanItem.MAX_EVIDENCE_REFS)
        List<@NotBlank @Size(max = 200) String> evidenceRefs
) {
}