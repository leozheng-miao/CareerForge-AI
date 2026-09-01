package com.leo.careerforgeai.interview.application.model.common;

/**
 * @program: CareerForge-AI
 * @description: 定义面试角色契约校验失败的稳定分类
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public enum InterviewRoleContractErrorType {

    INPUT_INVALID,
    OUTPUT_INVALID,
    REFERENCE_NOT_ALLOWED,
    SCORE_DIMENSION_MISMATCH
}