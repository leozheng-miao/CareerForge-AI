package com.leo.careerforgeai.agent.application.run.execution;

import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证Coaching Run执行器优雅关闭、超时中断和许可释放
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
class CoachingRunTaskExecutorShutdownTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID RUN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-20T11:00:00Z");

    private CoachingRunTaskExecutor taskExecutor;

    @AfterEach
    void closeExecutor() {
        if (taskExecutor != null) taskExecutor.close();
    }

    @Test
    void shouldWaitForRunningTaskToFinishDuringGracefulShutdown() throws Exception {
        CoachingRunAdmissionGate gate = gate(Duration.ofSeconds(1));
        taskExecutor = taskExecutor(Duration.ofSeconds(1));

        RunAdmissionLease lease = gate.tryAcquire(OWNER).orElseThrow();
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch allowTaskExit = new CountDownLatch(1);
        AtomicBoolean taskCompleted = new AtomicBoolean();

        Future<?> taskFuture = taskExecutor.submit(
                context(),
                lease,
                ignored -> {
                    taskStarted.countDown();
                    await(allowTaskExit);
                    taskCompleted.set(true);
                }
        );

        assertThat(taskStarted.await(2, TimeUnit.SECONDS)).isTrue();

        try (ExecutorService closeCaller = Executors.newSingleThreadExecutor()) {
            CountDownLatch closeStarted = new CountDownLatch(1);
            Future<?> closeFuture = closeCaller.submit(() -> {
                closeStarted.countDown();
                taskExecutor.close();
            });

            assertThat(closeStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(closeFuture.isDone()).isFalse();

            allowTaskExit.countDown();

            closeFuture.get(2, TimeUnit.SECONDS);
            taskFuture.get(2, TimeUnit.SECONDS);
        }

        assertThat(taskCompleted).isTrue();
        assertThat(lease.isReleased()).isTrue();
        assertThat(taskExecutor.isAccepting()).isFalse();

        RunAdmissionLease reacquired = gate.tryAcquire(OWNER).orElseThrow();
        reacquired.close();
    }

    @Test
    void shouldInterruptRunningTaskAfterGracePeriodAndWaitForCleanup() throws Exception {
        Duration gracePeriod = Duration.ofMillis(100);
        CoachingRunAdmissionGate gate = gate(gracePeriod);
        taskExecutor = taskExecutor(gracePeriod);

        RunAdmissionLease lease = gate.tryAcquire(OWNER).orElseThrow();
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);

        Future<?> taskFuture = taskExecutor.submit(
                context(),
                lease,
                ignored -> {
                    taskStarted.countDown();

                    try {
                        new CountDownLatch(1).await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        interrupted.countDown();
                    }
                }
        );

        assertThat(taskStarted.await(2, TimeUnit.SECONDS)).isTrue();

        taskExecutor.close();

        assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        taskFuture.get(2, TimeUnit.SECONDS);
        assertThat(lease.isReleased()).isTrue();
        assertThat(taskExecutor.isAccepting()).isFalse();

        RunAdmissionLease reacquired = gate.tryAcquire(OWNER).orElseThrow();
        reacquired.close();
    }

    private CoachingRunAdmissionGate gate(Duration gracePeriod) {
        return new CoachingRunAdmissionGate(
                new CoachingRunExecutionProperties(
                        1,
                        1,
                        Duration.ofSeconds(90),
                        gracePeriod
                )
        );
    }

    private CoachingRunTaskExecutor taskExecutor(Duration gracePeriod) {
        ExecutorService virtualThreadExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name("test-run-shutdown-", 0)
                        .factory()
        );
        ScheduledExecutorService deadlineScheduler =
                Executors.newSingleThreadScheduledExecutor();

        return new CoachingRunTaskExecutor(
                virtualThreadExecutor,
                deadlineScheduler,
                Clock.fixed(NOW, ZoneOffset.UTC),
                gracePeriod
        );
    }

    private RunExecutionContext context() {
        return new RunExecutionContext(
                OWNER,
                RUN_ID,
                "trace-shutdown",
                NOW,
                NOW.plusSeconds(60)
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待任务被中断", exception);
        }
    }
}