package com.leo.careerforgeai.interview.application.model.validation;

import com.leo.careerforgeai.interview.application.model.contract.InterviewReportDraft;
import com.leo.careerforgeai.interview.application.model.contract.InterviewReportInput;
import com.leo.careerforgeai.interview.domain.InterviewRole;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

/**
 * @program: CareerForge-AI
 * @description: 校验复盘报告各建议集合不重复且保持待确认语义
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Component
public final class InterviewReportRoleContract
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
        requireNoDuplicateOutput(output.proposedMemoryCandidates(), "proposedMemoryCandidates");
        requireNoDuplicateOutput(output.proposedTrainingPlanAdjustments(), "proposedTrainingPlanAdjustments");
    }
}