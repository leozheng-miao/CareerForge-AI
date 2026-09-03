package com.leo.careerforgeai.agent.application.run.execution;

import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.shared.actor.ActorId;
import io.micrometer.context.ContextRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Run在虚拟线程执行、上下文传递、MDC清理和许可释放
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
class CoachingRunTaskExecutorTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID RUN_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW =
            Instant.parse("2026-08-20T08:00:00Z");

    private CoachingRunAdmissionGate admissionGate;
    private CoachingRunTaskExecutor taskExecutor;
    private ScheduledExecutorService deadlineScheduler;

    @BeforeEach
    void setUp() {
        CoachingRunExecutionProperties properties =
                new CoachingRunExecutionProperties(
                        1,
                        1,
                        Duration.ofSeconds(1)
                );

        admissionGate = new CoachingRunAdmissionGate(properties);

        ExecutorService virtualThreadExecutor =
                Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual()
                                .name("test-careerforge-run-", 0)
                                .factory()
                );

        deadlineScheduler =
                Executors.newSingleThreadScheduledExecutor();

        taskExecutor = new CoachingRunTaskExecutor(
                virtualThreadExecutor,
                deadlineScheduler,
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties.shutdownGracePeriod()
        );
    }

    @AfterEach
    void closeExecutor() {
        taskExecutor.close();
        MDC.clear();
    }

    @Test
    void shouldExecuteOnVirtualThreadWithExplicitContextAndMdc() throws Exception {
        RunExecutionContext context = context();
        RunAdmissionLease lease =
                admissionGate.tryAcquire(OWNER).orElseThrow();

        AtomicBoolean virtualThread = new AtomicBoolean();
        AtomicReference<String> ownerMdc = new AtomicReference<>();
        AtomicReference<String> runMdc = new AtomicReference<>();
        AtomicReference<String> traceMdc = new AtomicReference<>();

        Future<?> future = taskExecutor.submit(
                context,
                lease,
                actual -> {
                    assertThat(actual).isSameAs(context);
                    virtualThread.set(Thread.currentThread().isVirtual());
                    ownerMdc.set(MDC.get("ownerId"));
                    runMdc.set(MDC.get("runId"));
                    traceMdc.set(MDC.get("traceId"));
                }
        );

        future.get(2, TimeUnit.SECONDS);

        assertThat(virtualThread).isTrue();
        assertThat(ownerMdc).hasValue(OWNER.value());
        assertThat(runMdc).hasValue(RUN_ID.toString());
        assertThat(traceMdc).hasValue("trace-run-1");
        assertThat(lease.isReleased()).isTrue();

        RunAdmissionLease reacquired =
                admissionGate.tryAcquire(OWNER).orElseThrow();
        reacquired.close();
    }

    @Test
    void shouldReleaseLeaseWhenTaskFails() throws Exception {
        RunAdmissionLease lease =
                admissionGate.tryAcquire(OWNER).orElseThrow();

        Future<?> future = taskExecutor.submit(
                context(),
                lease,
                ignored -> {
                    throw new IllegalStateException("可控任务失败");
                }
        );

        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

        assertThat(lease.isReleased()).isTrue();

        RunAdmissionLease reacquired =
                admissionGate.tryAcquire(OWNER).orElseThrow();
        reacquired.close();
    }

    @Test
    void shouldReleaseLeaseWhenExecutorRejectsSubmission() {
        taskExecutor.close();

        RunAdmissionLease lease =
                admissionGate.tryAcquire(OWNER).orElseThrow();

        assertThatThrownBy(() -> taskExecutor.submit(
                context(),
                lease,
                ignored -> {
                }
        )).isInstanceOf(RejectedExecutionException.class);

        assertThat(lease.isReleased()).isTrue();

        RunAdmissionLease reacquired =
                admissionGate.tryAcquire(OWNER).orElseThrow();
        reacquired.close();
    }

    @Test
    void shouldRejectExpiredContextBeforeSubmission() {
        RunAdmissionLease lease =
                admissionGate.tryAcquire(OWNER).orElseThrow();

        RunExecutionContext expired = new RunExecutionContext(
                OWNER,
                RUN_ID,
                "trace-expired",
                NOW.minusSeconds(10),
                NOW
        );

        assertThatThrownBy(() -> taskExecutor.submit(
                expired,
                lease,
                ignored -> {
                }
        )).isInstanceOf(RunExecutionDeadlineExceededException.class)
                .hasMessage("Coaching Run已经超过执行Deadline");

        assertThat(lease.isReleased()).isTrue();

        RunAdmissionLease reacquired =
                admissionGate.tryAcquire(OWNER).orElseThrow();
        reacquired.close();
    }

    @Test
    void shouldInterruptTaskAtDeadlineAndReleaseLeaseAfterExit()
            throws Exception {
        RunAdmissionLease lease =
                admissionGate.tryAcquire(OWNER).orElseThrow();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);

        RunExecutionContext context = new RunExecutionContext(
                OWNER,
                RUN_ID,
                "trace-timeout",
                NOW,
                NOW.plusMillis(200)
        );

        Future<?> future = taskExecutor.submit(
                context,
                lease,
                ignored -> {
                    started.countDown();
                    try {
                        new CountDownLatch(1).await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        interrupted.countDown();
                    }
                }
        );

        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                .isInstanceOf(CancellationException.class);

        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();

        /*
         * Future被标记为取消不等于任务清理已经结束。
         * close()会等待虚拟线程真正退出，之后才能验证permit已经释放。
         */
        taskExecutor.close();

        assertThat(lease.isReleased()).isTrue();

        RunAdmissionLease reacquired =
                admissionGate.tryAcquire(OWNER).orElseThrow();
        reacquired.close();
    }

    @Test
    void shouldPropagateRegisteredContextIntoVirtualThread() throws Exception {
        ThreadLocal<String> traceContext = new ThreadLocal<>();
        String accessorKey = getClass().getName() + ".trace";
        ContextRegistry registry = ContextRegistry.getInstance();
        registry.registerThreadLocalAccessor(accessorKey, traceContext);

        try {
            traceContext.set("parent-trace");
            AtomicReference<String> captured = new AtomicReference<>();
            RunAdmissionLease lease = admissionGate.tryAcquire(OWNER).orElseThrow();

            Future<?> future = taskExecutor.submit(
                    context(), lease, ignored -> captured.set(traceContext.get())
            );
            traceContext.remove();
            future.get(2, TimeUnit.SECONDS);

            assertThat(captured).hasValue("parent-trace");
            assertThat(lease.isReleased()).isTrue();
        } finally {
            traceContext.remove();
            registry.removeThreadLocalAccessor(accessorKey);
        }
    }

    private RunExecutionContext context() {
        return new RunExecutionContext(
                OWNER,
                RUN_ID,
                "trace-run-1",
                NOW,
                NOW.plusSeconds(60)
        );
    }
}