package com.leo.careerforgeai.agent.application.run.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 表示一次Coaching Run原子限流判定结果
 * @author: Miao Zheng
 * @date: 2026-08-23
 * @param allowed 是否允许创建新Run
 * @param remaining 当前窗口剩余可用次数
 * @param resetAfter 当前窗口距离重置的时间
 */
public record CoachingRunRateLimitDecision(
        boolean allowed,
        long remaining,
        Duration resetAfter
) {

    public CoachingRunRateLimitDecision {
        Objects.requireNonNull(resetAfter, "resetAfter不能为空");
        if (remaining < 0) throw new IllegalArgumentException("remaining不能小于0");
        if (resetAfter.isZero() || resetAfter.isNegative()) {
            throw new IllegalArgumentException("resetAfter必须大于0");
        }
        if (!allowed && remaining != 0) {
            throw new IllegalArgumentException("拒绝判定的remaining必须为0");
        }
    }
}