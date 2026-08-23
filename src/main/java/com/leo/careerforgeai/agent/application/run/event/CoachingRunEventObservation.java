package com.leo.careerforgeai.agent.application.run.event;

import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureErrorType;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 聚合MySQL Run事实与当前可读取的Redis短期事件
 * @author: Miao Zheng
 * @date: 2026-08-21
 * @param run MySQL中的Run事实
 * @param events Redis中可用的短期事件
 * @param redisErrorType Redis不可用时的稳定错误类型，可为空
 */
public record CoachingRunEventObservation(
        CoachingRun run,
        List<StoredCoachingRunEvent> events,
        RedisInfrastructureErrorType redisErrorType
) {

    public CoachingRunEventObservation {
        Objects.requireNonNull(run, "run不能为空");
        events = List.copyOf(Objects.requireNonNull(events, "events不能为空"));
        if (redisErrorType != null && !events.isEmpty()) {
            throw new IllegalArgumentException("Redis失败时不能同时返回Redis事件");
        }
        if (events.stream().anyMatch(event -> !run.runId().equals(event.runId()))) {
            throw new IllegalArgumentException("Redis事件与MySQL Run身份不一致");
        }
    }

    public boolean redisAvailable() {
        return redisErrorType == null;
    }
}