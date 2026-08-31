package com.leo.careerforgeai.interview.application.execution;

import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncDispatcher;
import com.leo.careerforgeai.agent.application.run.execution.RunAdmissionLease;
import com.leo.careerforgeai.agent.application.run.execution.RunExecutionContext;
import com.leo.careerforgeai.agent.application.run.execution.RunExecutionDeadlineExceededException;
import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.interview.application.graph.InterviewGraphExecutionService;
import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewLifecycleApplicationService;
import com.leo.careerforgeai.interview.application.session.MockInterviewVersionConflictException;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewRoundStatus;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
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
 * @description: 校验报告恢复边界并重新准入异步执行同一面试的失败报告节点
 * @author: Miao Zheng
 * @date: 2026-08-31
 */
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MockInterviewReportRetryApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewLifecycleApplicationService lifecycleService;
    private final InterviewRoundRepository roundRepository;
    private final InterviewGraphExecutionService graphExecutionService;
    private final MockInterviewAsyncTask asyncTask;
    private final CoachingRunAsyncDispatcher dispatcher;
    private final CoachingRunExecutionProperties executionProperties;
    private final Clock clock;

    public MockInterviewReportRetryApplicationService(
            CurrentActorProvider currentActorProvider,
            MockInterviewLifecycleApplicationService lifecycleService,
            InterviewRoundRepository roundRepository,
            InterviewGraphExecutionService graphExecutionService,
            MockInterviewAsyncTask asyncTask,
            CoachingRunAsyncDispatcher dispatcher,
            CoachingRunExecutionProperties executionProperties,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService不能为空");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository不能为空");
        this.graphExecutionService = Objects.requireNonNull(graphExecutionService, "graphExecutionService不能为空");
        this.asyncTask = Objects.requireNonNull(asyncTask, "asyncTask不能为空");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher不能为空");
        this.executionProperties = Objects.requireNonNull(executionProperties, "executionProperties不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    public MockInterviewSession submit(UUID interviewId, long expectedVersion) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion不能小于0");

        ActorId ownerId = Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
        MockInterviewSession current = lifecycleService.get(interviewId);
        requireOwner(ownerId, current);

        if (current.status() == InterviewStatus.GENERATING_REPORT
                || current.status() == InterviewStatus.AWAITING_CONFIRMATION) {
            return current;
        }
        if (current.status() != InterviewStatus.INTERRUPTED || current.version() != expectedVersion) {
            throw new MockInterviewVersionConflictException(interviewId, expectedVersion);
        }

        graphExecutionService.requireReportRecoveryBoundary(interviewId);
        requireReviewedRounds(ownerId, current);

        RunAdmissionLease lease = dispatcher.acquire(ownerId);
        boolean handedOff = false;
        try {
            MockInterviewSession accepted = lifecycleService.retryReportGeneration(interviewId, expectedVersion);
            RunExecutionContext context = context(ownerId, interviewId);
            try {
                dispatcher.dispatch(context, lease, asyncTask::recover);
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

    private void requireReviewedRounds(ActorId ownerId, MockInterviewSession session) {
        int questionCount = roundRepository.countQuestions(ownerId, session.interviewId());
        if (questionCount < 1 || questionCount > session.budgetPolicy().maxQuestions()) {
            throw new IllegalStateException("报告恢复时MySQL问题数量不合法");
        }

        for (int roundNo = 1; roundNo <= questionCount; roundNo++) {
            InterviewRound round = roundRepository
                    .findRoundByNumber(ownerId, session.interviewId(), roundNo)
                    .orElseThrow(() -> new IllegalStateException("报告恢复时MySQL缺少已完成回合"));
            if (round.status() != InterviewRoundStatus.REVIEWED) {
                throw new IllegalStateException("报告恢复前所有回合必须完成评审");
            }
        }
    }

    private RunExecutionContext context(ActorId ownerId, UUID interviewId) {
        Instant submittedAt = clock.instant();
        return new RunExecutionContext(
                ownerId,
                interviewId,
                "interview-report-retry-" + interviewId,
                submittedAt,
                submittedAt.plus(executionProperties.executionTimeout())
        );
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