package com.leo.careerforgeai.interview.application.model.validation;

import com.leo.careerforgeai.interview.domain.InterviewRole;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 表示面试角色输入或模型结构化输出未通过可信边界
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public final class InterviewRoleContractException extends RuntimeException {

    private final InterviewRole role;
    private final InterviewRoleContractErrorType errorType;

    public InterviewRoleContractException(
            InterviewRole role,
            InterviewRoleContractErrorType errorType,
            String safeMessage
    ) {
        super(safeMessage);
        this.role = Objects.requireNonNull(role, "role不能为空");
        this.errorType = Objects.requireNonNull(errorType, "errorType不能为空");
    }

    public InterviewRole role() {
        return role;
    }

    public InterviewRoleContractErrorType errorType() {
        return errorType;
    }
}