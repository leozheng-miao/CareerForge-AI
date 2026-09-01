package com.leo.careerforgeai.interview.domain.session;

/**
 * @program: CareerForge-AI
 * @description: 定义模拟面试不可恢复失败和中断的稳定业务错误码
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public enum InterviewFailureCode {

    INPUT_SNAPSHOT_UNAVAILABLE,
    MODEL_OUTPUT_INVALID,
    MODEL_CALL_FAILED,
    BUDGET_EXHAUSTED,
    EXECUTION_DEADLINE_EXCEEDED,
    APPLICATION_SHUTDOWN,
    CHECKPOINT_FAILED,
    PERSISTENCE_CONFLICT,
    INTERNAL_ERROR
}