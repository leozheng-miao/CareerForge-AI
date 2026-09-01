package com.leo.careerforgeai.interview.application.event;

import com.leo.careerforgeai.interview.domain.session.InterviewStatus;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 定义允许进入Redis Stream和SSE的模拟面试安全事件类型
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
public enum InterviewEventType {

    INTERVIEW_CREATED,
    QUESTION_GENERATION_STARTED,
    QUESTION_READY,
    WAITING_FOR_ANSWER,
    ANSWER_ACCEPTED,
    REVIEW_STARTED,
    REPORT_GENERATION_STARTED,
    REPORT_READY,
    WAITING_FOR_CONFIRMATION,
    INTERVIEW_COMPLETED,
    INTERVIEW_FAILED,
    INTERVIEW_INTERRUPTED,
    INTERVIEW_CANCELLED;

    public static InterviewEventType fromStatus(InterviewStatus status) {
        Objects.requireNonNull(status, "status不能为空");
        return switch (status) {
            case CREATED -> INTERVIEW_CREATED;
            case GENERATING_QUESTION -> QUESTION_GENERATION_STARTED;
            case WAITING_FOR_ANSWER -> QUESTION_READY;
            case REVIEWING -> ANSWER_ACCEPTED;
            case GENERATING_REPORT -> REPORT_GENERATION_STARTED;
            case AWAITING_CONFIRMATION -> REPORT_READY;
            case COMPLETED -> INTERVIEW_COMPLETED;
            case FAILED -> INTERVIEW_FAILED;
            case INTERRUPTED -> INTERVIEW_INTERRUPTED;
            case CANCELLED -> INTERVIEW_CANCELLED;
        };
    }

    public boolean isTerminal() {
        return this == INTERVIEW_COMPLETED
                || this == INTERVIEW_FAILED
                || this == INTERVIEW_INTERRUPTED
                || this == INTERVIEW_CANCELLED;
    }
}