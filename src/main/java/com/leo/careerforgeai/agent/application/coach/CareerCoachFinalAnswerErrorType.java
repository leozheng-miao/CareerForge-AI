package com.leo.careerforgeai.agent.application.coach;

/**
 * @program: CareerForge-AI
 * @description: 区分Career Coach最终回答在Agent结果、模型结构、工具结果和引用白名单上的校验失败。
 * @author: Miao Zheng
 * @date: 2026-08-07 03:30
 **/
public enum CareerCoachFinalAnswerErrorType {
    AGENT_RESULT_INVALID,
    MODEL_OUTPUT_INVALID,
    TOOL_RESULT_INVALID,
    CITATION_NOT_ALLOWED
}