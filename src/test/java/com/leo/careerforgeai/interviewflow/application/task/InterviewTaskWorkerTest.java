package com.leo.careerforgeai.interviewflow.application.task;

import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.ClaimedTask;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskType;
import com.leo.careerforgeai.interviewflow.application.task.InterviewTaskHandlerRegistry.InterviewTaskHandler;
import com.leo.careerforgeai.interviewflow.config.InterviewTaskExecutorProperties;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewTaskWorkerTest {

    private static final Instant NOW = Instant.parse("2026-10-06T09:00:00Z");
    private static final ActorId OWNER = new ActorId("task-owner");

    @Mock
    private InterviewTaskRepository taskRepository;

    @Mock
    private InterviewSessionRepository sessionRepository;

    @Mock
    private InterviewTaskDispatcher dispatcher;

    private final AtomicInteger handled = new AtomicInteger();
    private InterviewTaskWorker worker;

    @BeforeEach
    void setUp() {
        InterviewTaskHandler handler = new InterviewTaskHandler() {
            @Override
            public TaskType taskType() {
                return TaskType.PREPARE_INTERVIEW;
            }

            @Override
            public void handle(ClaimedTask task) {
                handled.incrementAndGet();
            }
        };
        worker = new InterviewTaskWorker(
                taskRepository,
                sessionRepository,
                new InterviewTaskHandlerRegistry(List.of(handler)),
                dispatcher,
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCompleteCurrentTaskOnlyOnce() {
        ClaimedTask task = task(1, 3, 1, 1);
        when(sessionRepository.findVersions(OWNER, task.interviewId()))
                .thenReturn(Optional.of(
                        new InterviewSessionRepository.SessionVersions(1, 1)
                ));
        when(taskRepository.markSucceeded(task, NOW)).thenReturn(true);

        var result = worker.execute(task);

        assertThat(result.status()).isEqualTo(
                InterviewTaskWorker.ExecutionStatus.SUCCEEDED
        );
        assertThat(handled).hasValue(1);
        verify(taskRepository).markSucceeded(task, NOW);
    }

    @Test
    void shouldRejectStalePlanBeforeHandlerCanWrite() {
        ClaimedTask task = task(1, 3, 1, 1);
        when(sessionRepository.findVersions(OWNER, task.interviewId()))
                .thenReturn(Optional.of(
                        new InterviewSessionRepository.SessionVersions(1, 2)
                ));
        when(taskRepository.markFailed(
                task,
                "STALE_RESULT",
                "任务输入或计划版本已经过期",
                NOW
        )).thenReturn(true);

        var result = worker.execute(task);

        assertThat(result.status()).isEqualTo(
                InterviewTaskWorker.ExecutionStatus.OBSOLETE
        );
        assertThat(handled).hasValue(0);
        verify(taskRepository, never()).markSucceeded(any(), any());
    }

    @Test
    void shouldRetryFailureUntilMaximumAttemptsThenFail() {
        InterviewTaskHandler failing = new InterviewTaskHandler() {
            @Override
            public TaskType taskType() {
                return TaskType.PREPARE_INTERVIEW;
            }

            @Override
            public void handle(ClaimedTask task) {
                throw new IllegalStateException("boom");
            }
        };
        worker = new InterviewTaskWorker(
                taskRepository,
                sessionRepository,
                new InterviewTaskHandlerRegistry(List.of(failing)),
                dispatcher,
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        ClaimedTask retryable = task(1, 2, 1, 1);
        ClaimedTask exhausted = task(2, 2, 1, 1);
        when(sessionRepository.findVersions(OWNER, retryable.interviewId()))
                .thenReturn(Optional.of(
                        new InterviewSessionRepository.SessionVersions(1, 1)
                ));
        when(taskRepository.markRetryWait(
                any(),
                any(),
                anyString(),
                anyString(),
                any()
        )).thenReturn(true);
        when(taskRepository.markFailed(
                any(),
                anyString(),
                anyString(),
                any()
        )).thenReturn(true);

        assertThat(worker.execute(retryable).status()).isEqualTo(
                InterviewTaskWorker.ExecutionStatus.RETRY_WAIT
        );
        assertThat(worker.execute(exhausted).status()).isEqualTo(
                InterviewTaskWorker.ExecutionStatus.FAILED
        );
    }

    @Test
    void shouldRescheduleCapacityRejectionWithoutSpendingExecutionAttempt() {
        ClaimedTask task = task(3, 3, 1, 1);
        when(taskRepository.claimNextAvailableTask(
                any(),
                anyString(),
                any()
        )).thenReturn(Optional.of(task));
        when(dispatcher.tryDispatch(any(), any(), any())).thenReturn(false);
        when(taskRepository.rescheduleUnstarted(
                task,
                NOW.plusSeconds(5),
                "CAPACITY_REJECTED",
                "面试任务执行容量已满",
                NOW
        )).thenReturn(true);

        assertThat(worker.pollAndDispatch(1)).isEqualTo(1);

        verify(taskRepository).rescheduleUnstarted(
                task,
                NOW.plusSeconds(5),
                "CAPACITY_REJECTED",
                "面试任务执行容量已满",
                NOW
        );
        verify(taskRepository, never()).markFailed(
                any(),
                anyString(),
                anyString(),
                any()
        );
    }

    @Test
    void shouldNotifyHandlerAfterTerminalTaskFailure() {
        AtomicReference<String> terminalFailure = new AtomicReference<>();
        InterviewTaskHandler failing = new InterviewTaskHandler() {
            @Override
            public TaskType taskType() {
                return TaskType.GENERATE_REPORT;
            }

            @Override
            public void handle(ClaimedTask task) {
                throw new IllegalStateException("boom");
            }

            @Override
            public void onTerminalFailure(ClaimedTask task, String errorCode) {
                terminalFailure.set(errorCode);
            }
        };
        worker = new InterviewTaskWorker(
                taskRepository,
                sessionRepository,
                new InterviewTaskHandlerRegistry(List.of(failing)),
                dispatcher,
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        ClaimedTask task = task(TaskType.GENERATE_REPORT, 3, 3, 1, 1);
        when(sessionRepository.findVersions(OWNER, task.interviewId()))
                .thenReturn(Optional.of(
                        new InterviewSessionRepository.SessionVersions(1, 1)
                ));
        when(taskRepository.markFailed(
                task,
                "TASK_EXECUTION_FAILED",
                "任务处理失败: IllegalStateException",
                NOW
        )).thenReturn(true);

        assertThat(worker.execute(task).status()).isEqualTo(
                InterviewTaskWorker.ExecutionStatus.FAILED
        );
        assertThat(terminalFailure).hasValue("TASK_EXECUTION_FAILED");
    }

    private static ClaimedTask task(
            int attempt,
            int maxAttempts,
            long inputVersion,
            long planVersion
    ) {
        return task(
                TaskType.PREPARE_INTERVIEW,
                attempt,
                maxAttempts,
                inputVersion,
                planVersion
        );
    }

    private static ClaimedTask task(
            TaskType taskType,
            int attempt,
            int maxAttempts,
            long inputVersion,
            long planVersion
    ) {
        return new ClaimedTask(
                UUID.fromString("43000000-0000-0000-0000-000000000001"),
                UUID.fromString("43000000-0000-0000-0000-000000000002"),
                OWNER,
                taskType,
                "prepare:task",
                attempt,
                maxAttempts,
                inputVersion,
                planVersion,
                "a".repeat(64),
                attempt,
                "lease-owner",
                NOW.plusSeconds(90)
        );
    }

    private static InterviewTaskExecutorProperties properties() {
        return new InterviewTaskExecutorProperties(
                true,
                8,
                8,
                2,
                Duration.ofSeconds(1),
                Duration.ofSeconds(90),
                Duration.ofSeconds(60),
                Duration.ofSeconds(5)
        );
    }
}
