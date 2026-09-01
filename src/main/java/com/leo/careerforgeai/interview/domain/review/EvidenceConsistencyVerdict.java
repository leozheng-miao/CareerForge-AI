package com.leo.careerforgeai.interview.domain.review;

/**
 * @program: CareerForge-AI
 * @description: 定义回答与冻结个人证据之间的一致性结论
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public enum EvidenceConsistencyVerdict {

    SUPPORTED,
    PARTIALLY_SUPPORTED,
    UNSUPPORTED,
    CONTRADICTED,
    NOT_APPLICABLE
}