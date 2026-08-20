package com.leo.careerforgeai.agent.application.run.execution;

import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证异步Dispatcher的容量拒绝、虚拟线程提交和许可释放
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
class CoachingRunAsyncDispatcherTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID RUN_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW =
            Instant.parse("2026-08-20T09:00:00Z");

    private CoachingRunAdmissionGate admissionGate;
    private CoachingRunTaskExecutor taskExecutor;
    private CoachingRunAsyncDispatcher dispatcher;
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
                                .name("test-run-dispatcher-", 0)
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

        dispatcher = new CoachingRunAsyncDispatcher(
                admissionGate,
                taskExecutor
        );
    }

    @AfterEach
    void closeExecutor() {
        taskExecutor.close();
    }

    @Test
    void shouldDispatchOnVirtualThreadAndReleaseCapacity() throws Exception {
        AtomicBoolean virtualThread = new AtomicBoolean();

        Future<?> future = dispatcher.dispatch(
                context(),
                ignored -> virtualThread.set(
                        Thread.currentThread().isVirtual()
                )
        );

        future.get(2, TimeUnit.SECONDS);

        assertThat(virtualThread).isTrue();

        RunAdmissionLease reacquired =
                admissionGate.tryAcquire(OWNER).orElseThrow();
        reacquired.close();
    }

    @Test
    void shouldRejectWhenCapacityIsExhausted() {
        RunAdmissionLease occupied =
                admissionGate.tryAcquire(OWNER).orElseThrow();

        AtomicBoolean executed = new AtomicBoolean();

        assertThatThrownBy(() -> dispatcher.dispatch(
                context(),
                ignored -> executed.set(true)
        )).isInstanceOf(CoachingRunCapacityRejectedException.class)
                .hasMessage("Coaching Run执行容量已满");

        assertThat(executed).isFalse();
        occupied.close();
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