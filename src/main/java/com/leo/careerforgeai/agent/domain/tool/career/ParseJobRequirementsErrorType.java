package com.leo.careerforgeai.agent.domain.tool.career;

/**
 * @program: CareerForge-AI
 * @description: 表示岗位解析失败时可安全返回给Agent的错误分类。
 * @author: Miao Zheng
 * @date: 2026-08-07 01:10
 **/
public enum ParseJobRequirementsErrorType {
    MODEL_CALL_FAILED,
    MODEL_OUTPUT_INVALID,
    UPSTREAM_TIMEOUT,
    INTERNAL_ERROR
}