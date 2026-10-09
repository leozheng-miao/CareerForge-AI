package com.leo.careerforgeai.interviewflow.domain.timer;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 保存服务端有效作答时间预算、累计用时和当前运行锚点
 * @author: Miao Zheng
 * @date: 2026-10-06
 * @param duration 总有效作答时间预算
 * @param accumulated 已累计的有效作答时间
 * @param state 当前计时状态
 * @param runningSince 当前运行片段的服务端开始时间
 */
public record InterviewTimer(
        Duration duration,
        Duration accumulated,
        TimerState state,
        Instant runningSince
) {

    public InterviewTimer {
        Objects.requireNonNull(duration, "duration不能为null");
        Objects.requireNonNull(accumulated, "accumulated不能为null");
        Objects.requireNonNull(state, "state不能为null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration必须大于0");
        }
        if (accumulated.isNegative()) {
            throw new IllegalArgumentException("accumulated不能小于0");
        }
        if (accumulated.compareTo(duration) > 0) {
            throw new IllegalArgumentException("accumulated不能超过duration");
        }
        if ((state == TimerState.RUNNING) != (runningSince != null)) {
            throw new IllegalArgumentException(
                    "仅RUNNING计时器可以保存runningSince"
            );
        }
    }

    public static InterviewTimer notStarted(Duration duration) {
        return new InterviewTimer(
                duration,
                Duration.ZERO,
                TimerState.NOT_STARTED,
                null
        );
    }

    public InterviewTimer start(Instant now) {
        Objects.requireNonNull(now, "now不能为null");
        if (state != TimerState.NOT_STARTED && state != TimerState.PAUSED) {
            throw new IllegalStateException(
                    "只有未开始或已暂停的计时器可以启动"
            );
        }
        if (accumulated.compareTo(duration) >= 0) {
            throw new IllegalStateException("有效作答时间已经耗尽");
        }
        return new InterviewTimer(
                duration,
                accumulated,
                TimerState.RUNNING,
                now
        );
    }

    public InterviewTimer pause(Instant now) {
        if (state != TimerState.RUNNING) {
            throw new IllegalStateException("只有运行中的计时器可以暂停");
        }
        Duration settled = settledAccumulated(now);
        return new InterviewTimer(
                duration,
                settled,
                settled.compareTo(duration) >= 0
                        ? TimerState.EXHAUSTED
                        : TimerState.PAUSED,
                null
        );
    }

    public InterviewTimer exhaust() {
        if (state != TimerState.RUNNING) {
            throw new IllegalStateException("只有运行中的计时器可以耗尽");
        }
        return new InterviewTimer(
                duration,
                duration,
                TimerState.EXHAUSTED,
                null
        );
    }

    public InterviewTimer stop(Instant now) {
        if (state == TimerState.STOPPED) {
            return this;
        }
        Duration settled = state == TimerState.RUNNING
                ? settledAccumulated(now)
                : accumulated;
        return new InterviewTimer(
                duration,
                settled,
                TimerState.STOPPED,
                null
        );
    }

    public Duration remaining(Instant now) {
        Duration effectiveAccumulated = state == TimerState.RUNNING
                ? settledAccumulated(now)
                : accumulated;
        return duration.minus(effectiveAccumulated);
    }

    private Duration settledAccumulated(Instant now) {
        Objects.requireNonNull(now, "now不能为null");
        if (now.isBefore(runningSince)) {
            throw new IllegalArgumentException(
                    "now不能早于runningSince"
            );
        }
        Duration candidate = accumulated.plus(
                Duration.between(runningSince, now)
        );
        return candidate.compareTo(duration) >= 0
                ? duration
                : candidate;
    }
}
