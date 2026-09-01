package com.leo.careerforgeai.interview.application.event;

import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureErrorType;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 聚合MySQL面试事实、Redis短期事件和Redis降级状态
 * @author: Miao Zheng
 * @date: 2026-08-30
 * @param session MySQL中的模拟面试事实
 * @param events Redis中可用的短期安全事件
 * @param redisErrorType Redis不可用时的稳定错误类型
 **/
public record InterviewEventObservation(
        MockInterviewSession session,
        List<StoredInterviewEvent> events,
        RedisInfrastructureErrorType redisErrorType
) {

    public InterviewEventObservation {
        Objects.requireNonNull(session, "session不能为空");
        events = List.copyOf(Objects.requireNonNull(events, "events不能为空"));

        if (redisErrorType != null && !events.isEmpty()) {
            throw new IllegalArgumentException("Redis失败时不能同时返回Redis事件");
        }
        if (events.stream().anyMatch(event ->
                !session.interviewId().equals(event.interviewId()))) {
            throw new IllegalArgumentException("Redis事件与MySQL面试身份不一致");
        }
    }

    public boolean redisAvailable() {
        return redisErrorType == null;
    }
}