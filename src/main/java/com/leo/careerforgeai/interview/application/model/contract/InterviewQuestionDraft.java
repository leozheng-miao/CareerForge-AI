package com.leo.careerforgeai.interview.application.model.contract;

import com.leo.careerforgeai.interview.domain.InterviewQuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 定义面试官角色生成但尚未持久化的结构化问题
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param questionType 问题类型
 * @param question 问题正文
 * @param targetSkills 本题考察的技能
 * @param difficulty 难度等级，范围1至5
 * @param evaluationPoints 技术评审应检查的要点
 * @param followUpAllowed 是否允许Java Supervisor安排追问
 * @param evidenceReferenceIds 本题实际引用的个人证据片段ID
 **/
public record InterviewQuestionDraft(
        @NotNull InterviewQuestionType questionType,
        @NotBlank @Size(max = 2_000) String question,
        @NotNull @Size(min = 1, max = 10) List<@NotBlank @Size(max = 100) String> targetSkills,
        @Min(1) @Max(5) int difficulty,
        @NotNull @Size(min = 1, max = 10) List<@NotBlank @Size(max = 500) String> evaluationPoints,
        boolean followUpAllowed,
        @NotNull @Size(max = 10) List<@Pattern(regexp = "[0-9a-f]{64}") String> evidenceReferenceIds
) {
}