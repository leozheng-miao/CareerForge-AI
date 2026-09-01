package com.leo.careerforgeai.interview.application.execution;

import com.leo.careerforgeai.agent.application.run.execution.RunExecutionContext;
import com.leo.careerforgeai.interview.application.graph.InterviewGraphExecutionService;
import com.leo.careerforgeai.interview.application.session.MockInterviewLifecycleApplicationService;
import com.leo.careerforgeai.interview.application.session.MockInterviewVersionConflictException;
import com.leo.careerforgeai.interview.domain.session.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 在受控异步线程中启动或恢复面试Graph并收敛Deadline、中断和未处理异常
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@Slf4j
@Component
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MockInterviewAsyncTask {

    private final InterviewGraphExecutionService graphExecutionService;
    private final MockInterviewLifecycleApplicationService lifecycleService;
    private final Clock clock;

    public MockInterviewAsyncTask(InterviewGraphExecutionService graphExecutionService,
                                  MockInterviewLifecycleApplicationService lifecycleService,
                                  Clock clock) {
        this.graphExecutionService = Objects.requireNonNull(graphExecutionService, "graphExecutionService不能为空");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    public void start(RunExecutionContext context) {
        execute(context, () -> graphExecutionService.start(context.runId()));
    }

    public void resumeAfterAnswer(RunExecutionContext context, UUID answerId) {
        Objects.requireNonNull(answerId, "answerId不能为空");
        execute(context, () -> graphExecutionService.resumeAfterAnswer(context.runId(), answerId));
    }

    public void recover(RunExecutionContext context) {
        execute(context, () -> graphExecutionService.recoverExecution(context.runId()));
    }

    private void execute(RunExecutionContext context, Runnable graphAction) {
        Objects.requireNonNull(context, "context不能为空");
        Objects.requireNonNull(graphAction, "graphAction不能为空");

        try {
            graphAction.run();
        } catch (RuntimeException | Error exception) {
            convergeWithoutMasking(context, failureCode(context), exception);
            throw exception;
        }

        if (context.isExpired(clock.instant()) || Thread.currentThread().isInterrupted()) {
            convergeWithoutMasking(context, failureCode(context), null);
        }
    }

    private InterviewFailureCode failureCode(RunExecutionContext context) {
        if (context.isExpired(clock.instant())) return InterviewFailureCode.EXECUTION_DEADLINE_EXCEEDED;
        if (Thread.currentThread().isInterrupted()) return InterviewFailureCode.APPLICATION_SHUTDOWN;
        return InterviewFailureCode.INTERNAL_ERROR;
    }

    private void convergeWithoutMasking(RunExecutionContext context,
                                        InterviewFailureCode failureCode,
                                        Throwable original) {
        boolean interrupted = Thread.interrupted();

        try {
            MockInterviewSession current = lifecycleService.get(context.runId());
            if (!current.isTerminal()) lifecycleService.interrupt(current.interviewId(), current.version(), failureCode);
        } catch (MockInterviewVersionConflictException exception) {
            log.info("异步面试失败收敛遇到版本竞争，interviewId={}", context.runId());
        } catch (RuntimeException persistenceFailure) {
            if (original != null) {
                original.addSuppressed(persistenceFailure);
            } else {
                log.error("异步面试失败终态保存失败，interviewId={}, failureCode={}, persistenceError={}",
                        context.runId(), failureCode, persistenceFailure.getClass().getSimpleName());
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}