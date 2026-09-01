package com.leo.careerforgeai.interview.application.model.question;

import com.leo.careerforgeai.interview.application.model.common.AbstractInterviewRoleContract;
import com.leo.careerforgeai.interview.application.model.common.InterviewRoleContractErrorType;
import com.leo.careerforgeai.interview.domain.execution.InterviewRole;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

/**
 * @program: CareerForge-AI
 * @description: 校验面试问题必须遵守蓝图题型、难度和证据引用白名单
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Component
public class InterviewQuestionRoleContract
        extends AbstractInterviewRoleContract<InterviewQuestionInput, InterviewQuestionDraft> {

    public InterviewQuestionRoleContract(Validator validator) {
        super(InterviewRole.INTERVIEWER, InterviewQuestionDraft.class, validator);
    }

    @Override
    protected void validateInputRules(InterviewQuestionInput input) {
        requireNoDuplicateInput(input.completedQuestionSummaries(), "completedQuestionSummaries");
    }

    @Override
    protected void validateOutputRules(InterviewQuestionInput input, InterviewQuestionDraft output) {
        if (output.questionType() != input.questionType()) {
            reject(InterviewRoleContractErrorType.OUTPUT_INVALID, "模型擅自改变了问题类型");
        }
        if (output.difficulty() != input.difficulty()) {
            reject(InterviewRoleContractErrorType.OUTPUT_INVALID, "模型擅自改变了问题难度");
        }

        requireAllowedReferences(output.evidenceReferenceIds(), input.evidenceByChunkId().keySet());
        requireNoDuplicateOutput(output.targetSkills(), "targetSkills");
        requireNoDuplicateOutput(output.evaluationPoints(), "evaluationPoints");
        requireNoDuplicateOutput(output.evidenceReferenceIds(), "evidenceReferenceIds");
    }
}