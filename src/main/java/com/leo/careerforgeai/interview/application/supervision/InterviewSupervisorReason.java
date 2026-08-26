package com.leo.careerforgeai.interview.application.supervision;

/**
 * @program: CareerForge-AI
 * @description: 定义Java Supervisor作出流程决策的稳定原因
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public enum InterviewSupervisorReason {

    FOLLOW_UP_RECOMMENDED,
    NEXT_QUESTION_ALLOWED,
    QUESTION_LIMIT_REACHED,
    NEXT_ROUND_BUDGET_INSUFFICIENT,
    UNRECOVERABLE_FAILURE,
    REPORT_BUDGET_INSUFFICIENT
}