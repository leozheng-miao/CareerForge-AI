package com.leo.careerforgeai.interview.application.model.report;

import com.leo.careerforgeai.interview.application.model.common.AbstractInterviewRoleContract;
import com.leo.careerforgeai.interview.application.model.common.InterviewRoleContractErrorType;
import com.leo.careerforgeai.interview.domain.execution.InterviewRole;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 校验复盘报告内容和结构化建议的唯一性及待确认语义
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Component
public class InterviewReportRoleContract
        extends AbstractInterviewRoleContract<InterviewReportInput, InterviewReportDraft> {

    public InterviewReportRoleContract(Validator validator) {
        super(InterviewRole.REPORT_COACH, InterviewReportDraft.class, validator);
    }

    @Override
    protected void validateInputRules(InterviewReportInput input) {
        requireNoDuplicateInput(input.roundReviewSummaries(), "roundReviewSummaries");
    }

    @Override
    protected void validateOutputRules(InterviewReportInput input, InterviewReportDraft output) {
        requireNoDuplicateOutput(output.strengths(), "strengths");
        requireNoDuplicateOutput(output.technicalGaps(), "technicalGaps");
        requireNoDuplicateOutput(output.evidenceExpressionRisks(), "evidenceExpressionRisks");
        requireNoDuplicateOutput(output.improvementActions(), "improvementActions");
        requireUniqueMemorySkills(output.proposedMemoryCandidates());
        requireUniqueTrainingFocusAreas(output.proposedTrainingPlanAdjustments());
    }

    private void requireUniqueMemorySkills(
            Iterable<InterviewReportSuggestionDraft.MemoryCandidate> candidates
    ) {
        Set<String> skills = new HashSet<>();
        for (InterviewReportSuggestionDraft.MemoryCandidate candidate : candidates) {
            if (candidate == null || !skills.add(normalizeKey(candidate.skillName()))) {
                reject(
                        InterviewRoleContractErrorType.OUTPUT_INVALID,
                        "proposedMemoryCandidates包含空元素或重复skillName"
                );
            }
        }
    }

    private void requireUniqueTrainingFocusAreas(
            Iterable<InterviewReportSuggestionDraft.TrainingPlanAdjustment> adjustments
    ) {
        Set<String> focusAreas = new HashSet<>();
        for (InterviewReportSuggestionDraft.TrainingPlanAdjustment adjustment : adjustments) {
            if (adjustment == null || !focusAreas.add(normalizeKey(adjustment.focusArea()))) {
                reject(
                        InterviewRoleContractErrorType.OUTPUT_INVALID,
                        "proposedTrainingPlanAdjustments包含空元素或重复focusArea"
                );
            }
        }
    }

    private String normalizeKey(String value) {
        return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}