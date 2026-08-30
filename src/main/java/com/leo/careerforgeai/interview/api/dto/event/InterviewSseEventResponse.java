package com.leo.careerforgeai.interview.api.dto.event;

import com.leo.careerforgeai.interview.application.event.InterviewEventType;
import com.leo.careerforgeai.interview.application.event.StoredInterviewEvent;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回不包含问题、答案、Prompt或模型内部数据的面试SSE事件
 * @author: Miao Zheng
 * @date: 2026-08-30
 * @param interviewId 模拟面试UUID
 * @param type Redis安全事件类型，MySQL状态快照为空
 * @param status 事件对应的面试状态
 * @param interviewVersion MySQL状态快照的乐观锁版本
 * @param failureCode MySQL失败终态的稳定错误码
 * @param source 事件来源
 * @param occurredAt 事件或状态更新时间
 **/
public record InterviewSseEventResponse(
        UUID interviewId,
        InterviewEventType type,
        InterviewStatus status,
        Long interviewVersion,
        InterviewFailureCode failureCode,
        InterviewSseEventSource source,
        Instant occurredAt
) {

    public InterviewSseEventResponse {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(source, "source不能为空");
        Objects.requireNonNull(occurredAt, "occurredAt不能为空");

        if (source == InterviewSseEventSource.REDIS_STREAM) {
            Objects.requireNonNull(type, "Redis事件type不能为空");
            if (interviewVersion != null || failureCode != null) {
                throw new IllegalArgumentException("Redis事件不能携带MySQL版本或失败码");
            }
        } else {
            if (type != null || interviewVersion == null || interviewVersion < 0) {
                throw new IllegalArgumentException("MySQL状态快照字段不合法");
            }
        }
    }

    public static InterviewSseEventResponse fromRedis(StoredInterviewEvent event) {
        return new InterviewSseEventResponse(
                event.interviewId(),
                event.type(),
                event.status(),
                null,
                null,
                InterviewSseEventSource.REDIS_STREAM,
                event.occurredAt()
        );
    }

    public static InterviewSseEventResponse fromMysql(MockInterviewSession session) {
        return new InterviewSseEventResponse(
                session.interviewId(),
                null,
                session.status(),
                session.version(),
                session.failureCode(),
                InterviewSseEventSource.MYSQL_STATE_SNAPSHOT,
                session.updatedAt()
        );
    }
}