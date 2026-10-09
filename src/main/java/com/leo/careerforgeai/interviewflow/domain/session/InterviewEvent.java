package com.leo.careerforgeai.interviewflow.domain.session;

/**
 * @program: CareerForge-AI
 * @description: 定义纯Java状态机可以处理的快速面试业务事件
 * @author: Miao Zheng
 * @date: 2026-09-30
 */
public enum InterviewEvent {

    PREPARATION_COMPLETED,
    FORMAL_START_REQUESTED,
    PAUSE_REQUESTED,
    RESUME_REQUESTED,
    ANSWER_SUBMITTED,
    ANSWER_SKIPPED,
    TIMER_EXHAUSTED,
    FOLLOW_UP_SELECTED,
    NEXT_TOPIC_SELECTED,
    FINISH_REQUESTED,
    REPORT_GENERATED
}
