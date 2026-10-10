package com.leo.careerforgeai.interviewflow.application.session;

import com.leo.careerforgeai.interviewflow.application.port.InterviewActivityLease;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.config.InterviewFlowTimerProperties;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewEvent;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewStateMachine;
import com.leo.careerforgeai.interviewflow.domain.session.QuickInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.session.ComprehensiveInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
import com.leo.careerforgeai.interviewflow.domain.timer.TimerState;
import com.leo.careerforgeai.interviewflow.domain.turn.QuestionStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 以MySQL计时事实和Redis活动租约管理心跳、暂停和恢复
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Slf4j
@Service
@ConditionalOnProperty(
        prefix = "careerforge.persistence",
        name = "enabled",
        havingValue = "true"
)
public class InterviewTimerApplicationService {

    private static final InterviewStateMachine STATE_MACHINE =
            new InterviewStateMachine(new QuickInterviewPolicy());

    private final CurrentActorProvider currentActorProvider;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewActivityLease activityLease;
    private final Clock clock;
    private final InterviewFlowTimerProperties properties;

    public InterviewTimerApplicationService(
            CurrentActorProvider currentActorProvider,
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            InterviewActivityLease activityLease,
            Clock clock,
            InterviewFlowTimerProperties properties
    ) {
        this.currentActorProvider = Objects.requireNonNull(
                currentActorProvider,
                "currentActorProvider不能为null"
        );
        this.sessionRepository = Objects.requireNonNull(
                sessionRepository,
                "sessionRepository不能为null"
        );
        this.questionRepository = Objects.requireNonNull(
                questionRepository,
                "questionRepository不能为null"
        );
        this.activityLease = Objects.requireNonNull(
                activityLease,
                "activityLease不能为null"
        );
        this.clock = Objects.requireNonNull(clock, "clock不能为null");
        this.properties = Objects.requireNonNull(
                properties,
                "properties不能为null"
        );
    }

    @Transactional
    public TimerStatus heartbeat(UUID interviewId) {
        Objects.requireNonNull(interviewId, "interviewId不能为null");
        ActorId ownerId = currentActorProvider.currentActor();
        InterviewAggregate aggregate = findOwned(ownerId, interviewId);
        requireInterviewing(aggregate);
        if (aggregate.timer().state() != TimerState.RUNNING) {
            revokeQuietly(ownerId, interviewId);
            return status(aggregate, false);
        }

        Instant now = clock.instant();
        if (aggregate.timer().remaining(now).isZero()) {
            InterviewAggregate exhausted = settleAndPause(
                    ownerId,
                    aggregate,
                    now
            );
            revokeQuietly(ownerId, interviewId);
            return status(exhausted, false);
        }

        try {
            activityLease.refresh(
                    ownerId,
                    interviewId,
                    properties.leaseDuration()
            );
            return status(aggregate, true);
        } catch (RuntimeException exception) {
            log.warn(
                    "面试活动租约刷新失败，将安全暂停，interviewId={}",
                    interviewId
            );
            InterviewAggregate paused = settleAndPause(
                    ownerId,
                    aggregate,
                    now
            );
            return status(paused, false);
        }
    }

    @Transactional
    public TimerStatus pause(UUID interviewId, long expectedVersion) {
        validateMutation(interviewId, expectedVersion);
        ActorId ownerId = currentActorProvider.currentActor();
        InterviewAggregate aggregate = findOwned(ownerId, interviewId);
        requireInterviewing(aggregate);
        if (aggregate.timer().state() == TimerState.PAUSED
                || aggregate.timer().state() == TimerState.EXHAUSTED) {
            revokeQuietly(ownerId, interviewId);
            return status(aggregate, false);
        }
        requireExpectedVersion(aggregate, expectedVersion);
        InterviewAggregate paused = settleAndPause(
                ownerId,
                aggregate,
                clock.instant()
        );
        revokeQuietly(ownerId, interviewId);
        return status(paused, false);
    }

    @Transactional
    public TimerStatus resume(UUID interviewId, long expectedVersion) {
        validateMutation(interviewId, expectedVersion);
        ActorId ownerId = currentActorProvider.currentActor();
        InterviewAggregate aggregate = findOwned(ownerId, interviewId);
        requireInterviewing(aggregate);
        if (aggregate.timer().state() == TimerState.RUNNING) {
            try {
                activityLease.refresh(
                        ownerId,
                        interviewId,
                        properties.leaseDuration()
                );
                return status(aggregate, true);
            } catch (RuntimeException exception) {
                InterviewAggregate paused = settleAndPause(
                        ownerId,
                        aggregate,
                        clock.instant()
                );
                return status(paused, false);
            }
        }
        if (aggregate.timer().state() == TimerState.EXHAUSTED) {
            revokeQuietly(ownerId, interviewId);
            return status(aggregate, false);
        }
        if (aggregate.timer().state() != TimerState.PAUSED) {
            throw new IllegalStateException("当前面试计时状态不能恢复");
        }
        requireExpectedVersion(aggregate, expectedVersion);
        boolean hasPresentedQuestion = questionRepository.findQuestions(
                        ownerId,
                        interviewId
                ).stream()
                .anyMatch(question -> question.status()
                        == QuestionStatus.PRESENTED);
        if (!hasPresentedQuestion) {
            throw new IllegalStateException(
                    "只有完整PRESENTED问题已可见时才能恢复计时"
            );
        }

        activityLease.refresh(
                ownerId,
                interviewId,
                properties.leaseDuration()
        );
        InterviewAggregate resumed = stateMachineFor(aggregate).transition(
                aggregate,
                InterviewEvent.RESUME_REQUESTED,
                clock.instant()
        ).aggregate();
        if (!sessionRepository.updateIfVersionMatches(
                ownerId,
                resumed,
                expectedVersion
        )) {
            revokeQuietly(ownerId, interviewId);
            throw new IllegalStateException(
                    "面试状态已变化，请刷新后重试"
            );
        }
        return status(resumed, true);
    }

    @Transactional
    public boolean pauseExpired(ActorId ownerId, UUID interviewId) {
        Objects.requireNonNull(ownerId, "ownerId不能为null");
        Objects.requireNonNull(interviewId, "interviewId不能为null");
        Optional<InterviewAggregate> current = sessionRepository.findById(
                ownerId,
                interviewId
        );
        if (current.isEmpty()
                || current.get().session().stage()
                != SessionStage.INTERVIEWING
                || current.get().timer().state() != TimerState.RUNNING) {
            return false;
        }
        InterviewAggregate aggregate = current.get();
        InterviewAggregate paused = stateMachineFor(aggregate).transition(
                aggregate,
                InterviewEvent.PAUSE_REQUESTED,
                clock.instant()
        ).aggregate();
        return sessionRepository.updateIfVersionMatches(
                ownerId,
                paused,
                aggregate.session().version()
        );
    }

    private InterviewAggregate settleAndPause(
            ActorId ownerId,
            InterviewAggregate aggregate,
            Instant now
    ) {
        InterviewAggregate paused = stateMachineFor(aggregate).transition(
                aggregate,
                InterviewEvent.PAUSE_REQUESTED,
                now
        ).aggregate();
        if (!sessionRepository.updateIfVersionMatches(
                ownerId,
                paused,
                aggregate.session().version()
        )) {
            InterviewAggregate latest = findOwned(
                    ownerId,
                    aggregate.session().interviewId()
            );
            if (latest.timer().state() != TimerState.RUNNING) {
                return latest;
            }
            throw new IllegalStateException(
                    "面试状态已变化，请刷新后重试"
            );
        }
        return paused;
    }

    private static InterviewStateMachine stateMachineFor(InterviewAggregate aggregate) {
        return aggregate.session().mode() == InterviewMode.COMPREHENSIVE
                ? new InterviewStateMachine(new ComprehensiveInterviewPolicy(
                        aggregate.timer().duration().toSeconds()))
                : STATE_MACHINE;
    }

    private InterviewAggregate findOwned(
            ActorId ownerId,
            UUID interviewId
    ) {
        return sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new InterviewFlowNotFoundException(
                        interviewId
                ));
    }

    private void requireInterviewing(InterviewAggregate aggregate) {
        if (aggregate.session().stage() != SessionStage.INTERVIEWING) {
            throw new IllegalStateException("只有正式进行中的面试可以计时");
        }
    }

    private void requireExpectedVersion(
            InterviewAggregate aggregate,
            long expectedVersion
    ) {
        if (aggregate.session().version() != expectedVersion) {
            throw new IllegalStateException(
                    "面试版本已变化，请刷新后重试"
            );
        }
    }

    private void validateMutation(UUID interviewId, long expectedVersion) {
        Objects.requireNonNull(interviewId, "interviewId不能为null");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "expectedVersion不能小于0"
            );
        }
    }

    private TimerStatus status(
            InterviewAggregate aggregate,
            boolean leaseActive
    ) {
        return new TimerStatus(
                aggregate.session().interviewId(),
                aggregate.timer().state(),
                aggregate.timer().remaining(clock.instant()).toSeconds(),
                aggregate.session().version(),
                leaseActive
        );
    }

    private void revokeQuietly(ActorId ownerId, UUID interviewId) {
        try {
            activityLease.revoke(ownerId, interviewId);
        } catch (RuntimeException exception) {
            log.warn(
                    "面试活动租约删除失败，MySQL计时事实已保持安全，interviewId={}",
                    interviewId
            );
        }
    }

    public record TimerStatus(
            UUID interviewId,
            TimerState timerState,
            long remainingSeconds,
            long version,
            boolean leaseActive
    ) {

        public TimerStatus {
            Objects.requireNonNull(interviewId, "interviewId不能为null");
            Objects.requireNonNull(timerState, "timerState不能为null");
            if (remainingSeconds < 0 || version < 0) {
                throw new IllegalArgumentException("计时响应版本或剩余时间不合法");
            }
        }
    }
}
