package com.leo.careerforgeai.interview.application.model.validation;

import com.leo.careerforgeai.interview.application.model.contract.TechnicalReviewDraft;
import com.leo.careerforgeai.interview.application.model.contract.TechnicalReviewInput;
import com.leo.careerforgeai.interview.domain.InterviewRole;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 校验技术评审只能使用Java指定的评分维度
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Component
public final class TechnicalReviewRoleContract
        extends AbstractInterviewRoleContract<TechnicalReviewInput, TechnicalReviewDraft> {

    public TechnicalReviewRoleContract(Validator validator) {
        super(InterviewRole.TECHNICAL_REVIEWER, TechnicalReviewDraft.class, validator);
    }

    @Override
    protected void validateInputRules(TechnicalReviewInput input) {
        requireNoDuplicateInput(input.scoreDimensions(), "scoreDimensions");
        requireNoDuplicateInput(input.scoringRubric(), "scoringRubric");
    }

    @Override
    protected void validateOutputRules(TechnicalReviewInput input, TechnicalReviewDraft output) {
        if (!output.dimensionScores().keySet().equals(Set.copyOf(input.scoreDimensions()))) {
            reject(InterviewRoleContractErrorType.SCORE_DIMENSION_MISMATCH, "模型评分维度与服务端Rubric不一致");
        }

        requireNoDuplicateOutput(output.coveredPoints(), "coveredPoints");
        requireNoDuplicateOutput(output.errorsOrOmissions(), "errorsOrOmissions");
        requireNoDuplicateOutput(output.verificationBasis(), "verificationBasis");
    }
}