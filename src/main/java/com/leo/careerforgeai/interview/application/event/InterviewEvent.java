package com.leo.careerforgeai.interview.application.event;

import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示经过Java白名单校验且不包含问题、答案或模型内部数据的面试事件
 * @author: Miao Zheng
 * @date: 2026-08-30
 * @param ownerId 面试所属用户
 * @param interviewId 模拟面试UUID
 * @param type 安全事件类型
 * @param status 事件对应的MySQL面试状态
 * @param occurredAt 事件发生时间
 **/
public record InterviewEvent(
        ActorId ownerId,
        UUID interviewId,
        InterviewEventType type,
        InterviewStatus status,
        Instant occurredAt
) {

    public InterviewEvent {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(type, "type不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(occurredAt, "occurredAt不能为空");
        validateStatus(type, status);
    }

    public static InterviewEvent state(ActorId ownerId,
                                       UUID interviewId,
                                       InterviewStatus status,
                                       Instant occurredAt) {
        return new InterviewEvent(
                ownerId,
                interviewId,
                InterviewEventType.fromStatus(status),
                status,
                occurredAt
        );
    }

    public InterviewEvent derived(InterviewEventType derivedType) {
        Objects.requireNonNull(derivedType, "derivedType不能为空");
        return new InterviewEvent(ownerId, interviewId, derivedType, status, occurredAt);
    }

    static void validateStatus(InterviewEventType type, InterviewStatus status) {
        InterviewStatus expected = switch (type) {
            case INTERVIEW_CREATED -> InterviewStatus.CREATED;
            case QUESTION_GENERATION_STARTED -> InterviewStatus.GENERATING_QUESTION;
            case QUESTION_READY, WAITING_FOR_ANSWER -> InterviewStatus.WAITING_FOR_ANSWER;
            case ANSWER_ACCEPTED, REVIEW_STARTED -> InterviewStatus.REVIEWING;
            case REPORT_GENERATION_STARTED -> InterviewStatus.GENERATING_REPORT;
            case REPORT_READY, WAITING_FOR_CONFIRMATION -> InterviewStatus.AWAITING_CONFIRMATION;
            case INTERVIEW_COMPLETED -> InterviewStatus.COMPLETED;
            case INTERVIEW_FAILED -> InterviewStatus.FAILED;
            case INTERVIEW_INTERRUPTED -> InterviewStatus.INTERRUPTED;
            case INTERVIEW_CANCELLED -> InterviewStatus.CANCELLED;
        };
        if (status != expected) throw new IllegalArgumentException("面试事件类型与状态不一致");
    }
}