package com.leo.careerforgeai.interviewflow.api.session;

import com.leo.careerforgeai.interviewflow.application.session.InterviewTimerApplicationService.TimerStatus;
import com.leo.careerforgeai.interviewflow.domain.timer.TimerState;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回面试计时器的安全服务端事实
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public record InterviewHeartbeatResponse(
        UUID interviewId,
        TimerState timerState,
        long remainingSeconds,
        long version,
        boolean leaseActive
) {

    public static InterviewHeartbeatResponse from(TimerStatus status) {
        Objects.requireNonNull(status, "status不能为null");
        return new InterviewHeartbeatResponse(
                status.interviewId(),
                status.timerState(),
                status.remainingSeconds(),
                status.version(),
                status.leaseActive()
        );
    }
}
