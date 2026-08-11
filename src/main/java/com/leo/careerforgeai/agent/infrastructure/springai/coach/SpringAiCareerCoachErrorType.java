package com.leo.careerforgeai.agent.infrastructure.springai.coach;

/**
 * @program: CareerForge-AI
 * @description: 分类Spring AI Career Coach模型、工具和框架执行失败。
 * @author: Miao Zheng
 * @date: 2026-08-10 03:40
 **/
public enum SpringAiCareerCoachErrorType {
    TRANSIENT_MODEL_FAILURE,
    NON_TRANSIENT_MODEL_FAILURE,
    TOOL_EXECUTION_FAILURE,
    LIMIT_EXCEEDED,
    TIMED_OUT,
    FRAMEWORK_FAILURE
}