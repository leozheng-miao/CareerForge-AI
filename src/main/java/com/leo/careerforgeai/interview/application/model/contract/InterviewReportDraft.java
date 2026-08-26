package com.leo.careerforgeai.interview.application.model.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 定义复盘教练角色生成但尚未由用户确认的结构化报告
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param strengths 已由面试事实支持的优势
 * @param technicalGaps 技术能力差距
 * @param evidenceExpressionRisks 证据表达或材料一致性风险
 * @param improvementActions 可执行改进动作
 * @param proposedMemoryCandidates 待用户确认的长期Memory建议
 * @param proposedTrainingPlanAdjustments 待用户确认的训练计划调整建议
 **/
public record InterviewReportDraft(
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 1_000) String> strengths,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 1_000) String> technicalGaps,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 1_000) String> evidenceExpressionRisks,
        @NotNull @Size(min = 1, max = 20) List<@NotBlank @Size(max = 1_000) String> improvementActions,
        @NotNull @Size(max = 10) List<@NotBlank @Size(max = 1_000) String> proposedMemoryCandidates,
        @NotNull @Size(max = 10) List<@NotBlank @Size(max = 1_000) String> proposedTrainingPlanAdjustments
) {
}