package com.leo.careerforgeai.interviewflow.application.session;

import com.leo.careerforgeai.interviewflow.application.port.InterviewActivityLease;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.config.InterviewFlowTimerProperties;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewEvent;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewSession;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewStateMachine;
import com.leo.careerforgeai.interviewflow.domain.session.QuickInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
import com.leo.careerforgeai.interviewflow.domain.timer.TimerState;
import com.leo.careerforgeai.interviewflow.domain.turn.InterviewQuestion;
import com.leo.careerforgeai.interviewflow.infrastructure.redis.InterviewLeaseExpirySweeper;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证有效作答时间、暂停恢复、租约失效和耗尽后完成当前题
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
class PauseResumeTimeoutTest {

    private static final ActorId OWNER = new ActorId("quick-owner-a");
    private static final UUID INTERVIEW_ID =
            UUID.fromString("34000000-0000-0000-0000-000000000001");
    private static final UUID TOPIC_ID =
            UUID.fromString("34000000-0000-0000-0000-000000000002");
    private static final UUID QUESTION_ID =
            UUID.fromString("34000000-0000-0000-0000-000000000003");
    private static final Instant NOW =
            Instant.parse("2026-10-06T03:00:00Z");

    private final InterviewFlowTimerProperties properties =
            new InterviewFlowTimerProperties(
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(15),
                    100
            );
    private final InMemorySessionRepository sessionRepository =
            new InMemorySessionRepository();
    private final FakeActivityLease activityLease =
            new FakeActivityLease();
    private final MutableClock clock = new MutableClock(NOW);
    private InterviewQuestionRepository questionRepository;
    private InterviewTimerApplicationService service;

    @BeforeEach
    void setUp() {
        questionRepository = mock(InterviewQuestionRepository.class);
        service = new InterviewTimerApplicationService(
                () -> OWNER,
                sessionRepository,
                questionRepository,
                activityLease,
                clock,
                properties
        );
    }

    @Test
    void shouldAccumulateOnlyTheRunningInterval() {
        InterviewAggregate running = runningInterview();
        sessionRepository.seed(running);

        clock.advance(Duration.ofSeconds(120));
        var paused = service.pause(
                INTERVIEW_ID,
                running.session().version()
        );

        assertThat(paused.timerState()).isEqualTo(TimerState.PAUSED);
        assertThat(paused.remainingSeconds()).isEqualTo(3480);
        assertThat(sessionRepository.get(INTERVIEW_ID)
                .timer().accumulated()).isEqualTo(Duration.ofSeconds(120));

        clock.advance(Duration.ofMinutes(5));
        var replay = service.pause(INTERVIEW_ID, paused.version());

        assertThat(replay.remainingSeconds()).isEqualTo(3480);
        assertThat(sessionRepository.updateCount()).isEqualTo(1);
    }

    @Test
    void shouldResumeOnlyWhenACompleteQuestionIsPresented() {
        InterviewAggregate paused = pausedInterview(Duration.ofSeconds(60));
        sessionRepository.seed(paused);
        when(questionRepository.findQuestions(OWNER, INTERVIEW_ID))
                .thenReturn(List.of(reservedQuestion()));

        assertThatThrownBy(() -> service.resume(
                INTERVIEW_ID,
                paused.session().version()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PRESENTED");

        when(questionRepository.findQuestions(OWNER, INTERVIEW_ID))
                .thenReturn(List.of(presentedQuestion()));
        var resumed = service.resume(
                INTERVIEW_ID,
                paused.session().version()
        );

        assertThat(resumed.timerState()).isEqualTo(TimerState.RUNNING);
        assertThat(resumed.remainingSeconds()).isEqualTo(3540);
        assertThat(activityLease.isActive(OWNER, INTERVIEW_ID)).isTrue();
    }

    @Test
    void shouldCapElapsedTimeAndKeepTheExhaustedQuestionAnswerable() {
        InterviewAggregate running = runningInterview();
        sessionRepository.seed(running);
        clock.advance(Duration.ofSeconds(4000));

        var exhausted = service.heartbeat(INTERVIEW_ID);

        InterviewAggregate stored = sessionRepository.get(INTERVIEW_ID);
        assertThat(exhausted.timerState()).isEqualTo(TimerState.EXHAUSTED);
        assertThat(exhausted.remainingSeconds()).isZero();
        assertThat(stored.timer().accumulated())
                .isEqualTo(Duration.ofSeconds(3600));
        assertThat(stored.session().stage())
                .isEqualTo(SessionStage.INTERVIEWING);
        assertThat(activityLease.refreshCount()).isZero();

        var answerResult = new InterviewStateMachine(
                new QuickInterviewPolicy()
        ).transition(
                stored,
                InterviewEvent.ANSWER_SUBMITTED,
                clock.instant()
        );
        assertThat(answerResult.aggregate().session().stage())
                .isEqualTo(SessionStage.GENERATING_REPORT);
        assertThat(answerResult.shouldGenerateReport()).isTrue();
    }

    @Test
    void shouldRefreshHeartbeatWithoutWritingMysql() {
        InterviewAggregate running = runningInterview();
        sessionRepository.seed(running);

        var heartbeat = service.heartbeat(INTERVIEW_ID);

        assertThat(heartbeat.leaseActive()).isTrue();
        assertThat(heartbeat.timerState()).isEqualTo(TimerState.RUNNING);
        assertThat(sessionRepository.updateCount()).isZero();
        assertThat(activityLease.refreshCount()).isEqualTo(1);
    }

    @Test
    void shouldPauseSafelyWhenRedisCannotRefreshTheLease() {
        InterviewAggregate running = runningInterview();
        sessionRepository.seed(running);
        activityLease.failRefresh = true;
        clock.advance(Duration.ofSeconds(20));

        var heartbeat = service.heartbeat(INTERVIEW_ID);

        assertThat(heartbeat.leaseActive()).isFalse();
        assertThat(heartbeat.timerState()).isEqualTo(TimerState.PAUSED);
        assertThat(heartbeat.remainingSeconds()).isEqualTo(3580);
        assertThat(sessionRepository.get(INTERVIEW_ID).timer().state())
                .isEqualTo(TimerState.PAUSED);
    }

    @Test
    void shouldSettleAndPauseTheRunningInterviewBeforeLogout() {
        InterviewAggregate running = runningInterview();
        sessionRepository.seed(running);
        activityLease.refresh(OWNER, INTERVIEW_ID, properties.leaseDuration());
        clock.advance(Duration.ofSeconds(25));

        service.beforeTokenRevocation(OWNER);

        InterviewAggregate stored = sessionRepository.get(INTERVIEW_ID);
        assertThat(stored.timer().state()).isEqualTo(TimerState.PAUSED);
        assertThat(stored.timer().accumulated()).isEqualTo(Duration.ofSeconds(25));
        assertThat(activityLease.isActive(OWNER, INTERVIEW_ID)).isFalse();
        assertThat(sessionRepository.updateCount()).isEqualTo(1);
    }

    @Test
    void shouldSettleAnExpiredLeaseExactlyOnce() {
        InterviewAggregate running = runningInterview();
        sessionRepository.seed(running);
        clock.advance(Duration.ofSeconds(40));
        InterviewLeaseExpirySweeper sweeper =
                new InterviewLeaseExpirySweeper(
                        sessionRepository,
                        activityLease,
                        service,
                        clock,
                        properties
                );

        assertThat(sweeper.sweepExpiredLeases()).isEqualTo(1);
        assertThat(sweeper.sweepExpiredLeases()).isZero();
        assertThat(sessionRepository.get(INTERVIEW_ID).timer().state())
                .isEqualTo(TimerState.PAUSED);
        assertThat(sessionRepository.updateCount()).isEqualTo(1);
    }

    private InterviewAggregate runningInterview() {
        InterviewAggregate aggregate = InterviewAggregate.createQuick(
                INTERVIEW_ID,
                OWNER,
                NOW,
                new QuickInterviewPolicy()
        );
        InterviewStateMachine stateMachine = new InterviewStateMachine(
                new QuickInterviewPolicy()
        );
        aggregate = stateMachine.transition(
                aggregate,
                InterviewEvent.PREPARATION_COMPLETED,
                NOW
        ).aggregate();
        return stateMachine.transition(
                aggregate,
                InterviewEvent.FORMAL_START_REQUESTED,
                NOW
        ).aggregate();
    }

    private InterviewAggregate pausedInterview(Duration elapsed) {
        InterviewAggregate running = runningInterview();
        return new InterviewStateMachine(new QuickInterviewPolicy())
                .transition(
                        running,
                        InterviewEvent.PAUSE_REQUESTED,
                        NOW.plus(elapsed)
                )
                .aggregate();
    }

    private InterviewQuestion reservedQuestion() {
        return InterviewQuestion.reserved(
                QUESTION_ID,
                TOPIC_ID,
                InterviewQuestion.QuestionType.MAIN,
                1,
                "请设计一个受限的Agent工作流。"
        );
    }

    private InterviewQuestion presentedQuestion() {
        return reservedQuestion().present();
    }

    private static final class InMemorySessionRepository
            implements InterviewSessionRepository {

        private final Map<UUID, InterviewAggregate> sessions =
                new ConcurrentHashMap<>();
        private int updateCount;

        void seed(InterviewAggregate aggregate) {
            sessions.put(aggregate.session().interviewId(), aggregate);
        }

        InterviewAggregate get(UUID interviewId) {
            return sessions.get(interviewId);
        }

        int updateCount() {
            return updateCount;
        }

        @Override
        public InterviewAggregate claim(SessionClaim claim) {
            sessions.put(
                    claim.candidate().session().interviewId(),
                    claim.candidate()
            );
            return claim.candidate();
        }

        @Override
        public Optional<InterviewAggregate> findById(
                ActorId ownerId,
                UUID interviewId
        ) {
            InterviewAggregate aggregate = sessions.get(interviewId);
            if (aggregate == null
                    || !aggregate.session().ownerId().equals(ownerId)) {
                return Optional.empty();
            }
            return Optional.of(aggregate);
        }

        @Override
        public Optional<InterviewAggregate> findByRequestId(
                ActorId ownerId,
                UUID requestId
        ) {
            return Optional.empty();
        }

        @Override
        public Optional<InterviewSession> findInProgress(ActorId ownerId) {
            return sessions.values().stream()
                    .map(InterviewAggregate::session)
                    .filter(session -> session.ownerId().equals(ownerId))
                    .filter(session -> session.stage()
                            == SessionStage.INTERVIEWING)
                    .findFirst();
        }

        @Override
        public boolean updateIfVersionMatches(
                ActorId ownerId,
                InterviewAggregate updated,
                long expectedVersion
        ) {
            InterviewAggregate current = sessions.get(
                    updated.session().interviewId()
            );
            if (current == null
                    || !current.session().ownerId().equals(ownerId)
                    || current.session().version() != expectedVersion) {
                return false;
            }
            sessions.put(updated.session().interviewId(), updated);
            updateCount++;
            return true;
        }

        @Override
        public List<InterviewAggregate> findRunningStartedBefore(
                Instant cutoff,
                int limit
        ) {
            List<InterviewAggregate> result = new ArrayList<>();
            for (InterviewAggregate aggregate : sessions.values()) {
                if (aggregate.timer().state() == TimerState.RUNNING
                        && aggregate.timer().runningSince() != null
                        && !aggregate.timer().runningSince().isAfter(cutoff)) {
                    result.add(aggregate);
                }
                if (result.size() == limit) {
                    break;
                }
            }
            return result;
        }
    }

    private static final class FakeActivityLease
            implements InterviewActivityLease {

        private final Map<String, Boolean> active = new ConcurrentHashMap<>();
        private int refreshCount;
        private boolean failRefresh;

        @Override
        public void refresh(
                ActorId ownerId,
                UUID interviewId,
                Duration leaseDuration
        ) {
            refreshCount++;
            if (failRefresh) {
                throw new IllegalStateException("Redis unavailable");
            }
            active.put(key(ownerId, interviewId), true);
        }

        @Override
        public boolean isActive(ActorId ownerId, UUID interviewId) {
            return active.getOrDefault(key(ownerId, interviewId), false);
        }

        @Override
        public void revoke(ActorId ownerId, UUID interviewId) {
            active.remove(key(ownerId, interviewId));
        }

        int refreshCount() {
            return refreshCount;
        }

        private String key(ActorId ownerId, UUID interviewId) {
            return ownerId.value() + ":" + interviewId;
        }
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
