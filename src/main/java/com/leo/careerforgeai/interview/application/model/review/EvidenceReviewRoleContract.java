package com.leo.careerforgeai.interview.application.model.review;

import com.leo.careerforgeai.interview.application.model.common.AbstractInterviewRoleContract;
import com.leo.careerforgeai.interview.application.model.common.InterviewRoleContractErrorType;
import com.leo.careerforgeai.interview.domain.review.EvidenceConsistencyVerdict;
import com.leo.careerforgeai.interview.domain.execution.InterviewRole;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

/**
 * @program: CareerForge-AI
 * @description: 校验证据结论、引用白名单及NOT_APPLICABLE确定性规则
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Component
public class EvidenceReviewRoleContract
        extends AbstractInterviewRoleContract<EvidenceReviewInput, EvidenceReviewDraft> {

    public EvidenceReviewRoleContract(Validator validator) {
        super(InterviewRole.EVIDENCE_REVIEWER, EvidenceReviewDraft.class, validator);
    }

    @Override
    protected void validateInputRules(EvidenceReviewInput input) {
    }

    @Override
    protected void validateOutputRules(EvidenceReviewInput input, EvidenceReviewDraft output) {
        boolean evidenceAvailable = !input.evidenceByChunkId().isEmpty();

        if (!evidenceAvailable && output.verdict() != EvidenceConsistencyVerdict.NOT_APPLICABLE) {
            reject(InterviewRoleContractErrorType.OUTPUT_INVALID, "无适用证据时结论必须是NOT_APPLICABLE");
        }
        if (evidenceAvailable && output.verdict() == EvidenceConsistencyVerdict.NOT_APPLICABLE) {
            reject(InterviewRoleContractErrorType.OUTPUT_INVALID, "存在适用证据时不能返回NOT_APPLICABLE");
        }
        if (output.verdict() == EvidenceConsistencyVerdict.NOT_APPLICABLE
                && !output.evidenceReferenceIds().isEmpty()) {
            reject(InterviewRoleContractErrorType.OUTPUT_INVALID, "NOT_APPLICABLE不能携带证据引用");
        }

        boolean referenceRequired = output.verdict() == EvidenceConsistencyVerdict.SUPPORTED
                || output.verdict() == EvidenceConsistencyVerdict.PARTIALLY_SUPPORTED
                || output.verdict() == EvidenceConsistencyVerdict.CONTRADICTED;

        if (referenceRequired && output.evidenceReferenceIds().isEmpty()) {
            reject(InterviewRoleContractErrorType.OUTPUT_INVALID, "当前证据结论必须提供引用");
        }

        requireAllowedReferences(output.evidenceReferenceIds(), input.evidenceByChunkId().keySet());
        requireNoDuplicateOutput(output.evidenceReferenceIds(), "evidenceReferenceIds");
    }
}