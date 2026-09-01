package com.leo.careerforgeai.interview.application.execution;

import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncDispatcher;
import com.leo.careerforgeai.agent.application.run.execution.RunAdmissionLease;
import com.leo.careerforgeai.agent.application.run.execution.RunExecutionContext;
import com.leo.careerforgeai.agent.application.run.execution.RunExecutionDeadlineExceededException;
import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.interview.application.answer.InterviewAnswerSubmissionService;
import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.question.CurrentInterviewQuestionUnavailableException;
import com.leo.careerforgeai.interview.application.session.MockInterviewLifecycleApplicationService;
import com.leo.careerforgeai.interview.domain.round.InterviewAnswer;
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
 * @description: 对答案提交执行幂等检查、容量准入、MySQL保存和Graph异步恢复
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MockInterviewAnswerAsyncSubmissionApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewLifecycleApplicationService lifecycleService;
    private final InterviewAnswerSubmissionService answerSubmissionService;
    private final InterviewRoundRepository roundRepository;
    private final MockInterviewAsyncTask asyncTask;
    private final CoachingRunAsyncDispatcher dispatcher;
    private final CoachingRunExecutionProperties executionProperties;
    private final Clock clock;

    public MockInterviewAnswerAsyncSubmissionApplicationService(
            CurrentActorProvider currentActorProvider,
            MockInterviewLifecycleApplicationService lifecycleService,
            InterviewAnswerSubmissionService answerSubmissionService,
            InterviewRoundRepository roundRepository,
            MockInterviewAsyncTask asyncTask,
            CoachingRunAsyncDispatcher dispatcher,
            CoachingRunExecutionProperties executionProperties,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService不能为空");
        this.answerSubmissionService = Objects.requireNonNull(answerSubmissionService, "answerSubmissionService不能为空");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository不能为空");
        this.asyncTask = Objects.requireNonNull(asyncTask, "asyncTask不能为空");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher不能为空");
        this.executionProperties = Objects.requireNonNull(executionProperties, "executionProperties不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    public MockInterviewSession submit(UUID interviewId,
                                       int roundNo,
                                       UUID questionId,
                                       UUID requestId,
                                       long expectedInterviewVersion,
                                       String answerText) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(questionId, "questionId不能为空");
        Objects.requireNonNull(requestId, "requestId不能为空");

        ActorId ownerId = Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");

        if (roundRepository.findAnswerByRequest(ownerId, requestId).isPresent()) {
            answerSubmissionService.submit(
                    interviewId,
                    roundNo,
                    questionId,
                    requestId,
                    expectedInterviewVersion,
                    answerText
            );
            return lifecycleService.get(interviewId);
        }

        MockInterviewSession current = lifecycleService.get(interviewId);
        requireOwner(ownerId, current);
        if (current.status() != InterviewStatus.WAITING_FOR_ANSWER) {
            throw new CurrentInterviewQuestionUnavailableException(interviewId, current.status());
        }

        RunAdmissionLease lease = dispatcher.acquire(ownerId);
        boolean handedOff = false;

        try {
            InterviewAnswer answer = answerSubmissionService.submit(
                    interviewId,
                    roundNo,
                    questionId,
                    requestId,
                    expectedInterviewVersion,
                    answerText
            );
            MockInterviewSession accepted = lifecycleService.get(interviewId);

            if (accepted.status() != InterviewStatus.REVIEWING
                    || accepted.version() != expectedInterviewVersion + 1) {
                return accepted;
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
                dispatcher.dispatch(
                        context,
                        lease,
                        executionContext -> asyncTask.resumeAfterAnswer(executionContext, answer.answerId())
                );
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