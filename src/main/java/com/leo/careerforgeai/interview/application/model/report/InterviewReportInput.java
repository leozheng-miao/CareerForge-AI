package com.leo.careerforgeai.interview.application.model.report;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义复盘教练所需事实及Java授权的优势、Memory和训练计划建议边界
 * @author: Miao Zheng
 * @date: 2026-08-31
 * @param interviewId 面试UUID
 * @param targetRoleSummary 已确认目标岗位摘要
 * @param roundReviewSummaries Java组装的回合评审摘要
 * @param allowedStrengths Java允许报告原样选择的优势
 * @param allowedMemoryCandidates Java允许模型原样选择的Memory候选
 * @param trainingPlanAdjustmentAllowed 当前冻结输入是否允许生成训练计划调整建议
 */
public record InterviewReportInput(
        @NotNull UUID interviewId,
        @NotBlank @Size(max = 8_000) String targetRoleSummary,
        @NotNull @Size(min = 1, max = 20) List<@NotBlank @Size(max = 4_000) String> roundReviewSummaries,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> allowedStrengths,
        @NotNull @Size(max = 10) List<@Valid AllowedMemoryCandidate> allowedMemoryCandidates,
        boolean trainingPlanAdjustmentAllowed
) {

    public InterviewReportInput {
        roundReviewSummaries = roundReviewSummaries == null ? null : List.copyOf(roundReviewSummaries);
        allowedStrengths = allowedStrengths == null ? null : List.copyOf(allowedStrengths);
        allowedMemoryCandidates = allowedMemoryCandidates == null ? null : List.copyOf(allowedMemoryCandidates);
        requireUniqueText(allowedStrengths, "allowedStrengths");
        if (allowedMemoryCandidates != null) {
            HashSet<String> skills = new HashSet<>();
            for (AllowedMemoryCandidate candidate : allowedMemoryCandidates) {
                if (candidate == null || !skills.add(candidate.skillName().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("allowedMemoryCandidates不能包含空元素或重复skillName");
                }
            }
        }
    }

    public InterviewReportInput(UUID interviewId, String targetRoleSummary, List<String> roundReviewSummaries) {
        this(interviewId, targetRoleSummary, roundReviewSummaries, List.of(), List.of(), false);
    }

    public InterviewReportInput(
            UUID interviewId,
            String targetRoleSummary,
            List<String> roundReviewSummaries,
            List<AllowedMemoryCandidate> allowedMemoryCandidates
    ) {
        this(interviewId, targetRoleSummary, roundReviewSummaries, List.of(), allowedMemoryCandidates, false);
    }

    public InterviewReportInput(
            UUID interviewId,
            String targetRoleSummary,
            List<String> roundReviewSummaries,
            List<String> allowedStrengths,
            List<AllowedMemoryCandidate> allowedMemoryCandidates
    ) {
        this(interviewId, targetRoleSummary, roundReviewSummaries, allowedStrengths, allowedMemoryCandidates, false);
    }

    private static void requireUniqueText(List<String> values, String field) {
        if (values == null) return;
        HashSet<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()
                    || !normalized.add(value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(field + "不能包含空值或重复内容");
            }
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义模型可以原样选择但不能改写的Memory候选
     * @author: Miao Zheng
     * @date: 2026-08-30
     * @param skillName Java确认的技能名称
     * @param content Java根据用户原始回答生成的能力证据
     */
    public record AllowedMemoryCandidate(
            @NotBlank @Size(max = 128) String skillName,
            @NotBlank @Size(max = 1_000) String content
    ) {

        public AllowedMemoryCandidate {
            if (skillName != null) skillName = skillName.strip().replaceAll("\\s+", " ");
            if (content != null) content = content.strip().replaceAll("\\s+", " ");
        }
    }
}