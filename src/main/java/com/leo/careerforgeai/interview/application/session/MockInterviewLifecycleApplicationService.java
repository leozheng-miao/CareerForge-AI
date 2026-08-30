package com.leo.careerforgeai.interview.application.session;

import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * @program: CareerForge-AI
 * @description: 使用CurrentActor、expectedVersion和数据库CAS推进模拟面试生命周期
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
@Service
public class MockInterviewLifecycleApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository repository;
    private final Clock clock;

    public MockInterviewLifecycleApplicationService(
            CurrentActorProvider currentActorProvider,
            MockInterviewSessionRepository repository,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    public MockInterviewSession get(UUID interviewId) {
        return requireOwnedSession(currentActor(), interviewId);
    }

    public MockInterviewSession startQuestionGeneration(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.startQuestionGeneration(clock.instant()));
    }

    public MockInterviewSession waitForAnswer(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.waitForAnswer(clock.instant()));
    }

    public MockInterviewSession startReview(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.startReview(clock.instant()));
    }

    public MockInterviewSession continueQuestioning(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.continueQuestioning(clock.instant()));
    }

    public MockInterviewSession startReportGeneration(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.startReportGeneration(clock.instant()));
    }

    public MockInterviewSession awaitConfirmation(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.awaitConfirmation(clock.instant()));
    }

    public MockInterviewSession complete(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.complete(clock.instant()));
    }

    public MockInterviewSession fail(
            UUID interviewId,
            long expectedVersion,
            InterviewFailureCode failureCode
    ) {
        Objects.requireNonNull(failureCode, "failureCode不能为空");
        return mutate(interviewId, expectedVersion, session -> session.fail(failureCode, clock.instant()));
    }

    public MockInterviewSession interrupt(
            UUID interviewId,
            long expectedVersion,
            InterviewFailureCode failureCode
    ) {
        Objects.requireNonNull(failureCode, "failureCode不能为空");
        return mutate(interviewId, expectedVersion, session -> session.interrupt(failureCode, clock.instant()));
    }

    public MockInterviewSession cancel(UUID interviewId, long expectedVersion) {
        return mutate(interviewId, expectedVersion, session -> session.cancel(clock.instant()));
    }

    private MockInterviewSession mutate(
            UUID interviewId,
            long expectedVersion,
            UnaryOperator<MockInterviewSession> mutation
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(mutation, "mutation不能为空");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion不能小于0");

        ActorId ownerId = currentActor();
        MockInterviewSession current = requireOwnedSession(ownerId, interviewId);

        if (current.version() != expectedVersion) {
            throw new MockInterviewVersionConflictException(interviewId, expectedVersion);
        }

        MockInterviewSession updated = mutation.apply(current);
        if (!repository.updateIfVersionMatches(ownerId, updated, expectedVersion)) {
            throw new MockInterviewVersionConflictException(interviewId, expectedVersion);
        }
        return updated;
    }

    private MockInterviewSession requireOwnedSession(ActorId ownerId, UUID interviewId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");

        MockInterviewSession session = repository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));

        if (!ownerId.equals(session.ownerId())) {
            throw new MockInterviewNotFoundException(interviewId);
        }
        return session;
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }
}