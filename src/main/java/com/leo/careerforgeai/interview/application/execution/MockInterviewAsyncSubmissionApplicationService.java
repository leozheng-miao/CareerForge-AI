package com.leo.careerforgeai.interview.application.execution;

import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncDispatcher;
import com.leo.careerforgeai.agent.application.run.execution.RunAdmissionLease;
import com.leo.careerforgeai.agent.application.run.execution.RunExecutionContext;
import com.leo.careerforgeai.agent.application.run.execution.RunExecutionDeadlineExceededException;
import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.interview.application.session.MockInterviewLifecycleApplicationService;
import com.leo.careerforgeai.interview.application.session.MockInterviewVersionConflictException;
import com.leo.careerforgeai.interview.domain.session.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.session.InterviewStatus;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 对面试启动执行owner准入、状态CAS认领、Deadline构造和虚拟线程移交
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MockInterviewAsyncSubmissionApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewLifecycleApplicationService lifecycleService;
    private final MockInterviewAsyncTask asyncTask;
    private final CoachingRunAsyncDispatcher dispatcher;
    private final CoachingRunExecutionProperties executionProperties;
    private final Clock clock;

    public MockInterviewAsyncSubmissionApplicationService(CurrentActorProvider currentActorProvider,
                                                          MockInterviewLifecycleApplicationService lifecycleService,
                                                          MockInterviewAsyncTask asyncTask,
                                                          CoachingRunAsyncDispatcher dispatcher,
                                                          CoachingRunExecutionProperties executionProperties,
                                                          Clock clock) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService不能为空");
        this.asyncTask = Objects.requireNonNull(asyncTask, "asyncTask不能为空");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher不能为空");
        this.executionProperties = Objects.requireNonNull(executionProperties, "executionProperties不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    public MockInterviewSession submitStart(UUID interviewId, long expectedVersion) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion不能小于0");

        ActorId ownerId = Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
        MockInterviewSession current = lifecycleService.get(interviewId);
        requireOwner(ownerId, current);

        if (current.status() != InterviewStatus.CREATED) return current;
        if (current.version() != expectedVersion) {
            throw new MockInterviewVersionConflictException(interviewId, expectedVersion);
        }

        RunAdmissionLease lease = dispatcher.acquire(ownerId);
        boolean handedOff = false;

        try {
            MockInterviewSession accepted;
            try {
                accepted = lifecycleService.startQuestionGeneration(interviewId, expectedVersion);
            } catch (MockInterviewVersionConflictException exception) {
                MockInterviewSession converged = lifecycleService.get(interviewId);
                if (converged.status() != InterviewStatus.CREATED) return converged;
                throw exception;
            }

            Instant submittedAt = clock.instant();
            RunExecutionContext context = new RunExecutionContext(
                    ownerId,
                    interviewId,
                    "interview-" + interviewId,
                    submittedAt,
                    submittedAt.plus(executionProperties.executionTimeout())
            );

            try {
                dispatcher.dispatch(context, lease, asyncTask::start);
                handedOff = true;
                return accepted;
            } catch (RuntimeException exception) {
                convergeDispatchFailure(accepted, exception);
                throw exception;
            }
        } finally {
            if (!handedOff) lease.close();
        }
    }

    private void convergeDispatchFailure(MockInterviewSession accepted, RuntimeException original) {
        InterviewFailureCode failureCode = original instanceof RunExecutionDeadlineExceededException
                ? InterviewFailureCode.EXECUTION_DEADLINE_EXCEEDED
                : InterviewFailureCode.APPLICATION_SHUTDOWN;
        try {
            lifecycleService.interrupt(accepted.interviewId(), accepted.version(), failureCode);
        } catch (RuntimeException persistenceFailure) {
            original.addSuppressed(persistenceFailure);
        }
    }

    private void requireOwner(ActorId ownerId, MockInterviewSession session) {
        if (!ownerId.equals(session.ownerId())) throw new IllegalStateException("模拟面试不属于当前用户");
    }
}