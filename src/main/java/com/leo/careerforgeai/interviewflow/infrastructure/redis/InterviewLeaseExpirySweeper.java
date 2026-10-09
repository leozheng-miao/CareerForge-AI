package com.leo.careerforgeai.interviewflow.infrastructure.redis;

import com.leo.careerforgeai.interviewflow.application.port.InterviewActivityLease;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.application.session.InterviewTimerApplicationService;
import com.leo.careerforgeai.interviewflow.config.InterviewFlowTimerProperties;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 有界扫描MySQL运行中计时器并将失效Redis租约安全收敛为暂停
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(
        prefix = "careerforge.persistence",
        name = "enabled",
        havingValue = "true"
)
public class InterviewLeaseExpirySweeper {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewActivityLease activityLease;
    private final InterviewTimerApplicationService timerService;
    private final Clock clock;
    private final InterviewFlowTimerProperties properties;

    public InterviewLeaseExpirySweeper(
            InterviewSessionRepository sessionRepository,
            InterviewActivityLease activityLease,
            InterviewTimerApplicationService timerService,
            Clock clock,
            InterviewFlowTimerProperties properties
    ) {
        this.sessionRepository = Objects.requireNonNull(
                sessionRepository,
                "sessionRepository不能为null"
        );
        this.activityLease = Objects.requireNonNull(
                activityLease,
                "activityLease不能为null"
        );
        this.timerService = Objects.requireNonNull(
                timerService,
                "timerService不能为null"
        );
        this.clock = Objects.requireNonNull(clock, "clock不能为null");
        this.properties = Objects.requireNonNull(
                properties,
                "properties不能为null"
        );
    }

    @Scheduled(
            fixedDelayString =
                    "${careerforge.interview-flow.timer.sweep-interval:15s}"
    )
    public int sweepExpiredLeases() {
        Instant cutoff = clock.instant().minus(properties.leaseDuration());
        List<InterviewAggregate> candidates =
                sessionRepository.findRunningStartedBefore(
                        cutoff,
                        properties.sweepBatchSize()
                );
        int pausedCount = 0;
        for (InterviewAggregate candidate : candidates) {
            boolean active;
            try {
                active = activityLease.isActive(
                        candidate.session().ownerId(),
                        candidate.session().interviewId()
                );
            } catch (RuntimeException exception) {
                active = false;
                log.warn(
                        "检查面试活动租约失败，按失效处理，interviewId={}",
                        candidate.session().interviewId()
                );
            }
            if (!active && timerService.pauseExpired(
                    candidate.session().ownerId(),
                    candidate.session().interviewId()
            )) {
                pausedCount++;
            }
        }
        return pausedCount;
    }
}
