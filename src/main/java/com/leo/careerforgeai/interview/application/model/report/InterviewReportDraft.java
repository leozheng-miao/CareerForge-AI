package com.leo.careerforgeai.interview.application.model.report;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 定义复盘教练角色生成但尚未由用户确认的有界结构化报告
 * @author: Miao Zheng
 * @date: 2026-08-31
 * @param strengths 已由面试事实支持的优势
 * @param technicalGaps 技术能力差距
 * @param evidenceExpressionRisks 证据表达或材料一致性风险
 * @param improvementActions 可执行改进动作
 * @param proposedMemoryCandidates 待用户确认的结构化Memory候选
 * @param proposedTrainingPlanAdjustments 待用户确认的结构化训练计划调整
 */
public record InterviewReportDraft(
        @NotNull @Size(max = 5) List<@NotBlank @Size(max = 500) String> strengths,
        @NotNull @Size(max = 5) List<@NotBlank @Size(max = 400) String> technicalGaps,
        @NotNull @Size(max = 5) List<@NotBlank @Size(max = 400) String> evidenceExpressionRisks,
        @NotNull @Size(min = 1, max = 5) List<@NotBlank @Size(max = 400) String> improvementActions,
        @NotNull @Size(max = 2) List<InterviewReportSuggestionDraft.@Valid MemoryCandidate> proposedMemoryCandidates,
        @NotNull @Size(max = 3) List<InterviewReportSuggestionDraft.@Valid TrainingPlanAdjustment> proposedTrainingPlanAdjustments
) {
}