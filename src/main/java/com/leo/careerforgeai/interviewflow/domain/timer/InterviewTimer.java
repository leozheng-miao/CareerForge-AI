package com.leo.careerforgeai.interviewflow.domain.timer;

import java.time.Duration;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 保存服务端有效作答时间预算、累计用时和运行状态
 * @author: Miao Zheng
 * @date: 2026-09-30
 * @param duration 总有效作答时间预算
 * @param accumulated 已累计的有效作答时间
 * @param state 当前计时状态
 */
public record InterviewTimer(
        Duration duration,
        Duration accumulated,
        TimerState state
) {

    public InterviewTimer {
        Objects.requireNonNull(duration, "duration不能为空");
        Objects.requireNonNull(accumulated, "accumulated不能为空");
        Objects.requireNonNull(state, "state不能为空");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration必须大于0");
        }
        if (accumulated.isNegative()) {
            throw new IllegalArgumentException("accumulated不能小于0");
        }
        if (accumulated.compareTo(duration) > 0) {
            throw new IllegalArgumentException("accumulated不能超过duration");
        }
    }

    public static InterviewTimer notStarted(Duration duration) {
        return new InterviewTimer(
                duration,
                Duration.ZERO,
                TimerState.NOT_STARTED
        );
    }

    public InterviewTimer start() {
        if (state != TimerState.NOT_STARTED && state != TimerState.PAUSED) {
            throw new IllegalStateException("只有未开始或已暂停的计时器可以启动");
        }
        if (accumulated.compareTo(duration) >= 0) {
            throw new IllegalStateException("有效作答时间已经耗尽");
        }
        return withState(TimerState.RUNNING);
    }

    public InterviewTimer pause() {
        if (state != TimerState.RUNNING) {
            throw new IllegalStateException("只有运行中的计时器可以暂停");
        }
        return withState(TimerState.PAUSED);
    }

    public InterviewTimer exhaust() {
        if (state != TimerState.RUNNING) {
            throw new IllegalStateException("只有运行中的计时器可以耗尽");
        }
        return new InterviewTimer(
                duration,
                duration,
                TimerState.EXHAUSTED
        );
    }

    public InterviewTimer stop() {
        if (state == TimerState.STOPPED) {
            return this;
        }
        return withState(TimerState.STOPPED);
    }

    private InterviewTimer withState(TimerState nextState) {
        return new InterviewTimer(duration, accumulated, nextState);
    }
}
