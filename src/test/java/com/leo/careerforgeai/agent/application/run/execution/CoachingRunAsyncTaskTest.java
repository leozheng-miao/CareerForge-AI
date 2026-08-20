package com.leo.careerforgeai.agent.application.run.execution;

import com.leo.careerforgeai.agent.application.run.CoachingRunExecutionApplicationService;
import com.leo.careerforgeai.agent.application.run.CoachingRunInterruptionApplicationService;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionVersionConflictException;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证异步Run成功、异常、Deadline、中断、Session漂移和原始异常保留
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@ExtendWith(MockitoExtension.class)
class CoachingRunAsyncTaskTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID RUN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-20T13:00:00Z");

    @Mock
    private CoachingRunExecutionApplicationService executionService;

    @Mock
    private CoachingRunInterruptionApplicationService interruptionService;

    private CoachingRunAsyncTask asyncTask;

    @BeforeEach
    void setUp() {
        asyncTask = new CoachingRunAsyncTask(
                executionService,
                interruptionService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldExecuteNormallyWithoutInterruptingRun() {
        RunExecutionContext context = activeContext();

        asyncTask.execute(context);

        verify(executionService).execute(context);
        verifyNoInteractions(interruptionService);
    }

    @Test
    void shouldInterruptRunWhenUnexpectedExecutionFails() {
        RunExecutionContext context = activeContext();
        IllegalStateException failure = new IllegalStateException("非预期执行失败");

        when(executionService.execute(context)).thenThrow(failure);

        assertThatThrownBy(() -> asyncTask.execute(context)).isSameAs(failure);

        verify(interruptionService).interruptForActor(
                OWNER,
                RUN_ID,
                "RUN_EXECUTION_ABORTED"
        );
    }

    @Test
    void shouldUseDeadlineFailureCodeWhenExecutionFailsAfterDeadline() {
        RunExecutionContext context = expiredContext();
        IllegalStateException failure = new IllegalStateException("Deadline后执行失败");

        when(executionService.execute(context)).thenThrow(failure);

        assertThatThrownBy(() -> asyncTask.execute(context)).isSameAs(failure);

        verify(interruptionService).interruptForActor(
                OWNER,
                RUN_ID,
                "RUN_DEADLINE_EXCEEDED"
        );
    }

    @Test
    void shouldInterruptRunWhenSessionVersionDrifts() {
        RunExecutionContext context = activeContext();
        CoachingSessionVersionConflictException failure =
                new CoachingSessionVersionConflictException("Session版本已经过期");

        when(executionService.execute(context)).thenThrow(failure);

        assertThatThrownBy(() -> asyncTask.execute(context)).isSameAs(failure);

        verify(interruptionService).interruptForActor(
                OWNER,
                RUN_ID,
                "SESSION_VERSION_DRIFT"
        );
    }

    @Test
    void shouldPersistInterruptedStateAndRestoreThreadInterruptFlag() throws Exception {
        RunExecutionContext context = activeContext();

        try (ExecutorService executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("test-async-run-interrupt-", 0).factory()
        )) {
            Future<Boolean> future = executor.submit(() -> {
                Thread.currentThread().interrupt();
                asyncTask.execute(context);
                return Thread.currentThread().isInterrupted();
            });

            assertThat(future.get(2, TimeUnit.SECONDS)).isTrue();
        }

        verify(executionService).execute(context);
        verify(interruptionService).interruptForActor(
                OWNER,
                RUN_ID,
                "RUN_EXECUTION_INTERRUPTED"
        );
    }

    @Test
    void shouldKeepOriginalFailureWhenInterruptedStateCannotBeSaved() {
        RunExecutionContext context = activeContext();
        IllegalStateException original = new IllegalStateException("原始执行失败");
        IllegalStateException persistenceFailure = new IllegalStateException("中断状态保存失败");

        when(executionService.execute(context)).thenThrow(original);
        when(interruptionService.interruptForActor(
                OWNER,
                RUN_ID,
                "RUN_EXECUTION_ABORTED"
        )).thenThrow(persistenceFailure);

        assertThatThrownBy(() -> asyncTask.execute(context))
                .isSameAs(original)
                .satisfies(exception ->
                        assertThat(exception.getSuppressed())
                                .containsExactly(persistenceFailure)
                );
    }

    private RunExecutionContext activeContext() {
        return new RunExecutionContext(
                OWNER,
                RUN_ID,
                "trace-active",
                NOW.minusSeconds(10),
                NOW.plusSeconds(60)
        );
    }

    private RunExecutionContext expiredContext() {
        return new RunExecutionContext(
                OWNER,
                RUN_ID,
                "trace-expired",
                NOW.minusSeconds(60),
                NOW
        );
    }
}