package com.leo.careerforgeai.interview.application.execution;

import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncDispatcher;
import com.leo.careerforgeai.agent.application.run.execution.RunExecutionContext;
import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @program: CareerForge-AI
 * @description: 应用启动后按owner扫描执行中面试并通过受控异步边界恢复Graph
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@Slf4j
@Component
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MockInterviewStartupRecovery {

    private static final int BATCH_SIZE = 100;

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository sessionRepository;
    private final CoachingRunAsyncDispatcher dispatcher;
    private final MockInterviewAsyncTask asyncTask;
    private final CoachingRunExecutionProperties executionProperties;
    private final Clock clock;
    private final Instant recoveryCutoff;

    public MockInterviewStartupRecovery(CurrentActorProvider currentActorProvider,
                                        MockInterviewSessionRepository sessionRepository,
                                        CoachingRunAsyncDispatcher dispatcher,
                                        MockInterviewAsyncTask asyncTask,
                                        CoachingRunExecutionProperties executionProperties,
                                        Clock clock) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher不能为空");
        this.asyncTask = Objects.requireNonNull(asyncTask, "asyncTask不能为空");
        this.executionProperties = Objects.requireNonNull(executionProperties, "executionProperties不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
        this.recoveryCutoff = clock.instant();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverExecutionRequiredInterviews() {
        ActorId ownerId = Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
        Set<UUID> attempted = new HashSet<>();
        int recoveredTotal = 0;

        while (!Thread.currentThread().isInterrupted()) {
            List<MockInterviewSession> candidates = sessionRepository.findExecutionRequiredUpdatedBefore(
                    ownerId, recoveryCutoff, BATCH_SIZE
            );
            if (candidates.isEmpty()) break;

            int attemptedInBatch = 0;
            for (MockInterviewSession candidate : candidates) {
                if (!attempted.add(candidate.interviewId())) continue;
                attemptedInBatch++;
                if (recover(ownerId, candidate)) recoveredTotal++;
                if (Thread.currentThread().isInterrupted()) break;
            }
            if (attemptedInBatch == 0) {
                log.error("模拟面试启动恢复无法继续，ownerId={}, blockedCandidateCount={}",
                        ownerId.value(), candidates.size());
                break;
            }
        }

        log.info("模拟面试启动恢复完成，ownerId={}, cutoff={}, recoveredCount={}",
                ownerId.value(), recoveryCutoff, recoveredTotal);
    }

    private boolean recover(ActorId ownerId, MockInterviewSession candidate) {
        if (!candidate.ownerId().equals(ownerId)) {
            log.error("模拟面试启动恢复候选owner不一致，interviewId={}", candidate.interviewId());
            return false;
        }

        Instant submittedAt = clock.instant();
        RunExecutionContext context = new RunExecutionContext(
                ownerId,
                candidate.interviewId(),
                "interview-recovery-" + candidate.interviewId(),
                submittedAt,
                submittedAt.plus(executionProperties.executionTimeout())
        );

        try {
            Future<?> future = dispatcher.dispatch(context, asyncTask::recover);
            future.get();
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("模拟面试启动恢复被中断，interviewId={}", candidate.interviewId());
            return false;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            log.error("模拟面试启动恢复执行失败，interviewId={}, errorType={}",
                    candidate.interviewId(), cause.getClass().getSimpleName());
            return false;
        } catch (RuntimeException exception) {
            log.error("模拟面试启动恢复提交失败，interviewId={}, errorType={}",
                    candidate.interviewId(), exception.getClass().getSimpleName());
            return false;
        }
    }
}