package com.leo.careerforgeai.agent.application.run.execution;

import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 使用同步点验证虚拟线程同时竞争时的全局和owner Run容量上限
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
class CoachingRunAdmissionGateConcurrencyTest {

    private static final int CONTENDER_COUNT = 32;

    @Test
    void shouldNotExceedGlobalCapacityUnderConcurrentVirtualThreads() throws Exception {
        CoachingRunAdmissionGate gate = new CoachingRunAdmissionGate(properties(3, 1));

        CompetitionResult result = compete(
                gate,
                index -> new ActorId("actor-" + index)
        );

        assertThat(result.allVirtual()).isTrue();
        assertThat(result.acquired()).isEqualTo(3);
        assertThat(result.maxActive()).isEqualTo(3);

        RunAdmissionLease lease = gate.tryAcquire(new ActorId("actor-after-global")).orElseThrow();
        lease.close();
    }

    @Test
    void shouldNotExceedOwnerCapacityUnderConcurrentVirtualThreads() throws Exception {
        CoachingRunAdmissionGate gate = new CoachingRunAdmissionGate(properties(8, 2));
        ActorId owner = new ActorId("actor-a");

        CompetitionResult result = compete(gate, ignored -> owner);

        assertThat(result.allVirtual()).isTrue();
        assertThat(result.acquired()).isEqualTo(2);
        assertThat(result.maxActive()).isEqualTo(2);

        RunAdmissionLease first = gate.tryAcquire(owner).orElseThrow();
        RunAdmissionLease second = gate.tryAcquire(owner).orElseThrow();
        assertThat(gate.tryAcquire(owner)).isEmpty();

        first.close();
        second.close();
    }

    private CompetitionResult compete(
            CoachingRunAdmissionGate gate,
            IntFunction<ActorId> ownerProvider
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(CONTENDER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch attempted = new CountDownLatch(CONTENDER_COUNT);
        CountDownLatch release = new CountDownLatch(1);

        AtomicBoolean allVirtual = new AtomicBoolean(true);
        AtomicInteger acquired = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("test-run-admission-", 0).factory()
        )) {
            for (int index = 0; index < CONTENDER_COUNT; index++) {
                ActorId ownerId = ownerProvider.apply(index);
                futures.add(executor.submit(() -> {
                    if (!Thread.currentThread().isVirtual()) allVirtual.set(false);
                    ready.countDown();
                    await(start);

                    Optional<RunAdmissionLease> acquiredLease = gate.tryAcquire(ownerId);
                    if (acquiredLease.isEmpty()) {
                        attempted.countDown();
                        return;
                    }

                    RunAdmissionLease lease = acquiredLease.orElseThrow();
                    acquired.incrementAndGet();
                    int currentActive = active.incrementAndGet();
                    maxActive.accumulateAndGet(currentActive, Math::max);
                    attempted.countDown();

                    try {
                        await(release);
                    } finally {
                        active.decrementAndGet();
                        lease.close();
                    }
                }));
            }

            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();

                return new CompetitionResult(
                        allVirtual.get(),
                        acquired.get(),
                        maxActive.get()
                );
            } finally {
                start.countDown();
                release.countDown();
                for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
            }
        }
    }

    private CoachingRunExecutionProperties properties(
            int maxConcurrentRuns,
            int maxConcurrentRunsPerOwner
    ) {
        return new CoachingRunExecutionProperties(
                maxConcurrentRuns,
                maxConcurrentRunsPerOwner,
                Duration.ofSeconds(90),
                Duration.ofSeconds(1)
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发测试线程被中断", exception);
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存一次受控并发竞争的虚拟线程和容量观测结果
     * @author: Miao Zheng
     * @date: 2026-08-20
     * @param allVirtual 所有竞争任务是否运行在虚拟线程
     * @param acquired 成功获得许可的任务数
     * @param maxActive 同一时刻观察到的最大活跃任务数
     **/
    private record CompetitionResult(
            boolean allVirtual,
            int acquired,
            int maxActive
    ) {
    }
}