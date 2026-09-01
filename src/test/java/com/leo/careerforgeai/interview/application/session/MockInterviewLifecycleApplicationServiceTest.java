package com.leo.careerforgeai.interview.application.session;

import com.leo.careerforgeai.interview.domain.session.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.session.InterviewMode;
import com.leo.careerforgeai.interview.domain.session.InterviewStatus;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import com.leo.careerforgeai.interview.support.FakeMockInterviewSessionRepository;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证模拟面试合法生命周期、非法迁移、owner隔离和CAS冲突
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
class MockInterviewLifecycleApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");
    private static final ActorId OWNER_A = new ActorId("owner-a");
    private static final ActorId OWNER_B = new ActorId("owner-b");

    @Test
    void shouldAdvanceOwnedInterviewThroughSuccessfulLifecycle() {
        AtomicReference<ActorId> actor = new AtomicReference<>(OWNER_A);
        FakeMockInterviewSessionRepository repository = new FakeMockInterviewSessionRepository();
        MockInterviewSession initial = newSession();
        repository.save(initial);

        MockInterviewLifecycleApplicationService service = service(actor, repository);
        MockInterviewSession generating = service.startQuestionGeneration(initial.interviewId(), 0);
        MockInterviewSession waiting = service.waitForAnswer(initial.interviewId(), 1);
        MockInterviewSession reviewing = service.startReview(initial.interviewId(), 2);
        MockInterviewSession reporting = service.startReportGeneration(initial.interviewId(), 3);
        MockInterviewSession confirming = service.awaitConfirmation(initial.interviewId(), 4);
        MockInterviewSession completed = service.complete(initial.interviewId(), 5);

        assertThat(generating.status()).isEqualTo(InterviewStatus.GENERATING_QUESTION);
        assertThat(waiting.status()).isEqualTo(InterviewStatus.WAITING_FOR_ANSWER);
        assertThat(reviewing.status()).isEqualTo(InterviewStatus.REVIEWING);
        assertThat(reporting.status()).isEqualTo(InterviewStatus.GENERATING_REPORT);
        assertThat(confirming.status()).isEqualTo(InterviewStatus.AWAITING_CONFIRMATION);
        assertThat(completed.status()).isEqualTo(InterviewStatus.COMPLETED);
        assertThat(completed.version()).isEqualTo(6);
        assertThat(completed.isTerminal()).isTrue();
    }

    @Test
    void shouldRejectIllegalTransitionStaleVersionOtherOwnerAndCasConflict() {
        AtomicReference<ActorId> actor = new AtomicReference<>(OWNER_A);
        FakeMockInterviewSessionRepository repository = new FakeMockInterviewSessionRepository();
        MockInterviewSession initial = newSession();
        repository.save(initial);

        MockInterviewLifecycleApplicationService service = service(actor, repository);

        assertThatThrownBy(() -> service.startReview(initial.interviewId(), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非法面试状态迁移");

        assertThatThrownBy(() -> service.startQuestionGeneration(initial.interviewId(), 1))
                .isInstanceOf(MockInterviewVersionConflictException.class);

        actor.set(OWNER_B);
        assertThatThrownBy(() -> service.get(initial.interviewId()))
                .isInstanceOf(MockInterviewNotFoundException.class);

        actor.set(OWNER_A);
        repository.forceNextConflict();
        assertThatThrownBy(() -> service.startQuestionGeneration(initial.interviewId(), 0))
                .isInstanceOf(MockInterviewVersionConflictException.class);

        assertThat(repository.findStored(initial.interviewId()).status()).isEqualTo(InterviewStatus.CREATED);
        assertThat(repository.findStored(initial.interviewId()).version()).isZero();
    }

    @Test
    void shouldCancelIdempotentlyAndRejectOtherOwnerOrCompletedInterview() {
        AtomicReference<ActorId> actor = new AtomicReference<>(OWNER_A);
        FakeMockInterviewSessionRepository repository = new FakeMockInterviewSessionRepository();
        MockInterviewSession initial = newSession();
        repository.save(initial);
        MockInterviewLifecycleApplicationService service = service(actor, repository);

        MockInterviewSession cancelled = service.cancel(initial.interviewId(), 0);
        MockInterviewSession replay = service.cancel(initial.interviewId(), 0);

        assertThat(cancelled.status()).isEqualTo(InterviewStatus.CANCELLED);
        assertThat(cancelled.version()).isEqualTo(1);
        assertThat(replay).isEqualTo(cancelled);
        assertThat(repository.findStored(initial.interviewId())).isEqualTo(cancelled);

        MockInterviewSession other = newSession();
        repository.save(other);
        actor.set(OWNER_B);
        assertThatThrownBy(() -> service.cancel(other.interviewId(), 0))
                .isInstanceOf(MockInterviewNotFoundException.class);

        actor.set(OWNER_A);
        MockInterviewSession completed = other.startQuestionGeneration(NOW)
                .waitForAnswer(NOW)
                .startReview(NOW)
                .startReportGeneration(NOW)
                .awaitConfirmation(NOW)
                .complete(NOW);
        repository.save(completed);

        assertThatThrownBy(() -> service.cancel(completed.interviewId(), completed.version()))
                .isInstanceOf(MockInterviewCancellationConflictException.class)
                .satisfies(exception -> assertThat(
                        ((MockInterviewCancellationConflictException) exception).status()
                ).isEqualTo(InterviewStatus.COMPLETED));
        assertThat(repository.findStored(completed.interviewId())).isEqualTo(completed);
    }

    private MockInterviewLifecycleApplicationService service(
            AtomicReference<ActorId> actor,
            FakeMockInterviewSessionRepository repository
    ) {
        return new MockInterviewLifecycleApplicationService(
                actor::get,
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private MockInterviewSession newSession() {
        return MockInterviewSession.create(
                UUID.randomUUID(),
                OWNER_A,
                UUID.randomUUID(),
                "a".repeat(64),
                InterviewMode.TARGETED_MOCK,
                UUID.randomUUID(),
                "b".repeat(64),
                new InterviewBudgetPolicy(5, 2, 20, 20_000),
                NOW
        );
    }
}