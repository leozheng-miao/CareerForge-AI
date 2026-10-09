package com.leo.careerforgeai.interviewflow.application.session;

import com.leo.careerforgeai.interviewflow.application.port.InterviewInputSnapshotRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewEvent;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewSession;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewStateMachine;
import com.leo.careerforgeai.interviewflow.domain.session.QuickInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @program: CareerForge-AI
 * @description: 验证同一用户两个标签页只能进入一场正式面试
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
class InterviewEntryConcurrencyTest {

    private static final ActorId OWNER = new ActorId("quick-owner-a");
    private static final Instant NOW =
            Instant.parse("2026-10-06T02:00:00Z");
    private static final UUID FIRST_ID =
            UUID.fromString("33000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_ID =
            UUID.fromString("33000000-0000-0000-0000-000000000002");

    @Test
    void shouldAllowExactlyOneReadyInterviewToBecomeActive() throws Exception {
        SingleActiveSessionRepository repository =
                new SingleActiveSessionRepository();
        repository.seed(readyInterview(FIRST_ID));
        repository.seed(readyInterview(SECOND_ID));

        InterviewSessionApplicationService service =
                new InterviewSessionApplicationService(
                        () -> OWNER,
                        repository,
                        mock(InterviewInputSnapshotRepository.class),
                        mock(InterviewTaskRepository.class),
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );
        CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Object> first = executor.submit(() -> enter(
                    service,
                    startGate,
                    FIRST_ID
            ));
            Future<Object> second = executor.submit(() -> enter(
                    service,
                    startGate,
                    SECOND_ID
            ));
            startGate.countDown();

            Object firstResult = first.get();
            Object secondResult = second.get();

            assertThat(java.util.List.of(firstResult, secondResult)
                    .stream()
                    .filter(InterviewSession.class::isInstance)
                    .toList())
                    .hasSize(1);
            assertThat(java.util.List.of(firstResult, secondResult)
                    .stream()
                    .filter(InterviewInProgressException.class::isInstance)
                    .toList())
                    .hasSize(1);

            InterviewInProgressException conflict =
                    (InterviewInProgressException) java.util.List.of(
                                    firstResult,
                                    secondResult
                            ).stream()
                            .filter(InterviewInProgressException.class::isInstance)
                            .findFirst()
                            .orElseThrow();
            assertThat(conflict.recovery().interviewId())
                    .isEqualTo(repository.activeInterviewId());
            assertThat(conflict.recovery().mode().name()).isEqualTo("QUICK");
            assertThat(conflict.recovery().currentQuestionNumber()).isEqualTo(1);
            assertThat(conflict.recovery().remainingSeconds()).isEqualTo(3600);
            assertThat(conflict.recovery().getClass().getRecordComponents())
                    .extracting(component -> component.getName())
                    .containsExactly(
                            "interviewId",
                            "mode",
                            "currentQuestionNumber",
                            "remainingSeconds"
                    );
        }
    }

    private Object enter(
            InterviewSessionApplicationService service,
            CountDownLatch startGate,
            UUID interviewId
    ) throws InterruptedException {
        startGate.await();
        try {
            return service.enter(
                    interviewId,
                    UUID.randomUUID(),
                    1
            );
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private InterviewAggregate readyInterview(UUID interviewId) {
        InterviewAggregate preparing = InterviewAggregate.createQuick(
                interviewId,
                OWNER,
                NOW,
                new QuickInterviewPolicy()
        );
        return new InterviewStateMachine(new QuickInterviewPolicy())
                .transition(preparing, InterviewEvent.PREPARATION_COMPLETED)
                .aggregate();
    }

    private static final class SingleActiveSessionRepository
            implements InterviewSessionRepository {

        private final Map<UUID, InterviewAggregate> sessions =
                new ConcurrentHashMap<>();
        private UUID activeInterviewId;

        void seed(InterviewAggregate aggregate) {
            sessions.put(aggregate.session().interviewId(), aggregate);
        }

        synchronized UUID activeInterviewId() {
            return activeInterviewId;
        }

        @Override
        public InterviewAggregate claim(SessionClaim claim) {
            throw new UnsupportedOperationException();
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
        public synchronized Optional<InterviewSession> findInProgress(
                ActorId ownerId
        ) {
            if (activeInterviewId == null) {
                return Optional.empty();
            }
            InterviewSession session = sessions.get(activeInterviewId).session();
            return session.ownerId().equals(ownerId)
                    ? Optional.of(session)
                    : Optional.empty();
        }

        @Override
        public synchronized boolean updateIfVersionMatches(
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
            if (updated.session().stage() == SessionStage.INTERVIEWING
                    && activeInterviewId != null
                    && !activeInterviewId.equals(
                            updated.session().interviewId()
                    )) {
                throw new DataIntegrityViolationException(
                        "active_owner_id unique conflict"
                );
            }
            sessions.put(updated.session().interviewId(), updated);
            if (updated.session().stage() == SessionStage.INTERVIEWING) {
                activeInterviewId = updated.session().interviewId();
            }
            return true;
        }
    }
}
