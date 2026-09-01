package com.leo.careerforgeai.interview.application.event;

import com.leo.careerforgeai.interview.domain.session.InterviewStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 表示从Redis Stream严格还原的模拟面试安全事件
 * @author: Miao Zheng
 * @date: 2026-08-30
 * @param eventId Redis Stream事件ID
 * @param interviewId 模拟面试UUID
 * @param type 安全事件类型
 * @param status 事件对应的MySQL状态
 * @param occurredAt 事件发生时间
 **/
public record StoredInterviewEvent(
        String eventId,
        UUID interviewId,
        InterviewEventType type,
        InterviewStatus status,
        Instant occurredAt
) {

    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("\\d+-\\d+");

    public StoredInterviewEvent {
        if (eventId == null || !EVENT_ID_PATTERN.matcher(eventId).matches()) {
            throw new IllegalArgumentException("eventId格式不合法");
        }
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(type, "type不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(occurredAt, "occurredAt不能为空");
        InterviewEvent.validateStatus(type, status);
    }
}