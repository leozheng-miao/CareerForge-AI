package com.leo.careerforgeai.agent.application.run.execution;

import com.leo.careerforgeai.agent.application.run.lifecycle.CoachingRunInterruptionApplicationService;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionVersionConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 执行异步Coaching Run并在异常、Deadline、中断或Session漂移时收敛耐久状态
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Component
@Slf4j
public class CoachingRunAsyncTask {

    private static final String DEADLINE_EXCEEDED = "RUN_DEADLINE_EXCEEDED";
    private static final String EXECUTION_INTERRUPTED = "RUN_EXECUTION_INTERRUPTED";
    private static final String EXECUTION_ABORTED = "RUN_EXECUTION_ABORTED";
    private static final String SESSION_VERSION_DRIFT = "SESSION_VERSION_DRIFT";

    private final CoachingRunExecutionApplicationService executionService;
    private final CoachingRunInterruptionApplicationService interruptionService;
    private final Clock clock;

    public CoachingRunAsyncTask(
            CoachingRunExecutionApplicationService executionService,
            CoachingRunInterruptionApplicationService interruptionService,
            Clock clock
    ) {
        this.executionService = Objects.requireNonNull(executionService, "executionService不能为空");
        this.interruptionService = Objects.requireNonNull(interruptionService, "interruptionService不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    public void execute(RunExecutionContext context) {
        Objects.requireNonNull(context, "context不能为空");

        try {
            executionService.execute(context);
        } catch (RuntimeException | Error exception) {
            interruptWithoutMasking(
                    context,
                    failureCode(context, exception),
                    exception
            );
            throw exception;
        }

        if (Thread.currentThread().isInterrupted()) {
            interruptWithoutMasking(
                    context,
                    failureCode(context, null),
                    null
            );
        }
    }

    private String failureCode(
            RunExecutionContext context,
            Throwable failure
    ) {
        if (context.isExpired(clock.instant())) return DEADLINE_EXCEEDED;
        if (failure instanceof CoachingSessionVersionConflictException) return SESSION_VERSION_DRIFT;
        if (Thread.currentThread().isInterrupted()) return EXECUTION_INTERRUPTED;
        return EXECUTION_ABORTED;
    }

    private void interruptWithoutMasking(
            RunExecutionContext context,
            String failureCode,
            Throwable original
    ) {
        boolean interrupted = Thread.interrupted();

        try {
            interruptionService.interruptForActor(
                    context.ownerId(),
                    context.runId(),
                    failureCode
            );
        } catch (RuntimeException persistenceException) {
            if (original != null) {
                original.addSuppressed(persistenceException);
            } else {
                log.error(
                        "异步Run中断状态保存失败，runId={}, failureCode={}, persistenceError={}",
                        context.runId(),
                        failureCode,
                        persistenceException.getClass().getSimpleName()
                );
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}