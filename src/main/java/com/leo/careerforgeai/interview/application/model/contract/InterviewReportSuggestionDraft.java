package com.leo.careerforgeai.interview.application.model.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @program: CareerForge-AI
 * @description: 定义Report Coach可生成但必须经用户确认的结构化可执行建议
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
public sealed interface InterviewReportSuggestionDraft
        permits InterviewReportSuggestionDraft.MemoryCandidate,
        InterviewReportSuggestionDraft.TrainingPlanAdjustment {

    /**
     * @program: CareerForge-AI
     * @description: 定义可转换为SKILL_EVIDENCE类型PENDING Memory的建议
     * @author: Miao Zheng
     * @date: 2026-08-29
     * @param skillName Java用于生成MemoryNormalizedKey的技能名称
     * @param content 待用户再次确认的能力证据正文
     */
    record MemoryCandidate(
            @NotBlank @Size(max = 128) String skillName,
            @NotBlank @Size(max = 1_000) String content
    ) implements InterviewReportSuggestionDraft {
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义生成下一版待确认训练计划时必须考虑的调整建议
     * @author: Miao Zheng
     * @date: 2026-08-29
     * @param focusArea 需要调整的技能或训练主题
     * @param adjustment 对下一版训练计划的具体调整要求
     */
    record TrainingPlanAdjustment(
            @NotBlank @Size(max = 128) String focusArea,
            @NotBlank @Size(max = 1_000) String adjustment
    ) implements InterviewReportSuggestionDraft {
    }
}