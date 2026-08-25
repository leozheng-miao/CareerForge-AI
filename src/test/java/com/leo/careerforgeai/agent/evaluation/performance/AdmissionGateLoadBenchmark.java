package com.leo.careerforgeai.agent.evaluation.performance;

import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAdmissionGate;
import com.leo.careerforgeai.agent.application.run.execution.RunAdmissionLease;
import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 使用同时到达的虚拟线程测量全局与owner两级Run准入舱壁
 * @author: Miao Zheng
 * @date: 2026-08-25
 */
class AdmissionGateLoadBenchmark {

    @Test
    void shouldMeasureBurstAdmissionAndReleaseEveryPermit() {
        int contenders = Integer.getInteger(
                "careerforge.benchmark.contenders",
                1_000
        );
        int globalPermits = Integer.getInteger(
                "careerforge.benchmark.global-permits",
                64
        );
        int ownerPermits = Integer.getInteger(
                "careerforge.benchmark.owner-permits",
                4
        );
        int ownerCount = Integer.getInteger(
                "careerforge.benchmark.owner-count",
                20
        );

        AdmissionLoadReport report = runBurst(
                contenders,
                globalPermits,
                ownerPermits,
                ownerCount
        );

        assertThat(report.accepted() + report.rejected()
                + report.failures()).isEqualTo(contenders);
        assertThat(report.accepted())
                .isLessThanOrEqualTo(globalPermits);
        assertThat(report.maxAcceptedForSingleOwner())
                .isLessThanOrEqualTo(ownerPermits);
        assertThat(report.rejected()).isPositive();
        assertThat(report.failures()).isZero();
        assertThat(report.capacityRestored()).isTrue();

        System.out.printf(
                Locale.ROOT,
                "admission contenders=%d accepted=%d rejected=%d "
                        + "failures=%d globalPermits=%d ownerPermits=%d "
                        + "owners=%d maxAcceptedPerOwner=%d wall=%.2fms restored=%s%n",
                report.contenders(),
                report.accepted(),
                report.rejected(),
                report.failures(),
                report.globalPermits(),
                report.ownerPermits(),
                report.ownerCount(),
                report.maxAcceptedForSingleOwner(),
                report.wallDurationNanos() / 1_000_000.0,
                report.capacityRestored()
        );
    }

    private AdmissionLoadReport runBurst(
            int contenders,
            int globalPermits,
            int ownerPermits,
            int ownerCount
    ) {
        if (contenders < 1) {
            throw new IllegalArgumentException(
                    "contenders必须大于0"
            );
        }
        if (ownerCount < 1) {
            throw new IllegalArgumentException(
                    "ownerCount必须大于0"
            );
        }

        CoachingRunExecutionProperties properties =
                new CoachingRunExecutionProperties(
                        globalPermits,
                        ownerPermits,
                        Duration.ofSeconds(90),
                        Duration.ofSeconds(1)
                );
        CoachingRunAdmissionGate gate =
                new CoachingRunAdmissionGate(properties);

        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch attemptsCompleted =
                new CountDownLatch(contenders);
        CountDownLatch releaseWinners = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(contenders);

        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        ConcurrentMap<ActorId, AtomicInteger> acceptedByOwner =
                new ConcurrentHashMap<>();

        long startedAt;
        long finishedAt;

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < contenders; index++) {
                ActorId ownerId = new ActorId(
                        "benchmark-owner-" + index % ownerCount
                );

                executor.execute(() -> executeAttempt(
                        gate,
                        ownerId,
                        ready,
                        start,
                        attemptsCompleted,
                        releaseWinners,
                        finished,
                        accepted,
                        rejected,
                        failures,
                        acceptedByOwner
                ));
            }

            requireLatch(
                    ready,
                    "并发任务未全部到达起跑点"
            );

            startedAt = System.nanoTime();
            start.countDown();

            try {
                requireLatch(
                        attemptsCompleted,
                        "准入尝试未全部完成"
                );
            } finally {
                releaseWinners.countDown();
            }

            requireLatch(
                    finished,
                    "准入许可未全部释放"
            );
            finishedAt = System.nanoTime();
        } finally {
            start.countDown();
            releaseWinners.countDown();
        }

        int maxAcceptedForSingleOwner =
                acceptedByOwner.values().stream()
                        .mapToInt(AtomicInteger::get)
                        .max()
                        .orElse(0);

        boolean capacityRestored = verifyCapacityRestored(
                gate,
                globalPermits
        );

        return new AdmissionLoadReport(
                contenders,
                accepted.get(),
                rejected.get(),
                failures.get(),
                globalPermits,
                ownerPermits,
                ownerCount,
                maxAcceptedForSingleOwner,
                finishedAt - startedAt,
                capacityRestored
        );
    }

    private void executeAttempt(
            CoachingRunAdmissionGate gate,
            ActorId ownerId,
            CountDownLatch ready,
            CountDownLatch start,
            CountDownLatch attemptsCompleted,
            CountDownLatch releaseWinners,
            CountDownLatch finished,
            AtomicInteger accepted,
            AtomicInteger rejected,
            AtomicInteger failures,
            ConcurrentMap<ActorId, AtomicInteger> acceptedByOwner
    ) {
        RunAdmissionLease lease = null;
        boolean classified = false;

        try {
            ready.countDown();
            start.await();

            Optional<RunAdmissionLease> acquired =
                    gate.tryAcquire(ownerId);

            if (acquired.isPresent()) {
                lease = acquired.get();
                accepted.incrementAndGet();
                acceptedByOwner.computeIfAbsent(
                        ownerId,
                        ignored -> new AtomicInteger()
                ).incrementAndGet();
            } else {
                rejected.incrementAndGet();
            }

            classified = true;
        } catch (InterruptedException exception) {
            if (!classified) failures.incrementAndGet();
            Thread.currentThread().interrupt();
        } catch (RuntimeException exception) {
            if (!classified) failures.incrementAndGet();
        } finally {
            attemptsCompleted.countDown();
        }

        try {
            if (lease != null) releaseWinners.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            if (lease != null) lease.close();
            finished.countDown();
        }
    }

    private boolean verifyCapacityRestored(
            CoachingRunAdmissionGate gate,
            int globalPermits
    ) {
        List<RunAdmissionLease> leases = new ArrayList<>();
        boolean restored = true;

        try {
            for (int index = 0; index < globalPermits; index++) {
                Optional<RunAdmissionLease> acquired =
                        gate.tryAcquire(new ActorId(
                                "restore-owner-" + index
                        ));

                if (acquired.isEmpty()) {
                    restored = false;
                    break;
                }
                leases.add(acquired.get());
            }

            Optional<RunAdmissionLease> overflow =
                    gate.tryAcquire(new ActorId(
                            "restore-overflow-owner"
                    ));

            if (overflow.isPresent()) {
                overflow.get().close();
                restored = false;
            }
        } finally {
            leases.forEach(RunAdmissionLease::close);
        }

        return restored;
    }

    private void requireLatch(
            CountDownLatch latch,
            String failureMessage
    ) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException(failureMessage);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    failureMessage,
                    exception
            );
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存一次Run两级舱壁突发准入的数量、耗时和释放结果
     * @author: Miao Zheng
     * @date: 2026-08-25
     * @param contenders 同时竞争许可的任务数
     * @param accepted 成功取得两级许可的任务数
     * @param rejected 零等待拒绝的任务数
     * @param failures 探针自身执行失败数
     * @param globalPermits 全局许可上限
     * @param ownerPermits 单owner许可上限
     * @param ownerCount 参与竞争的owner数量
     * @param maxAcceptedForSingleOwner 单owner实际最大成功数
     * @param wallDurationNanos 整次突发准入耗时
     * @param capacityRestored 结束后全部许可是否恢复
     */
    private record AdmissionLoadReport(
            int contenders,
            int accepted,
            int rejected,
            int failures,
            int globalPermits,
            int ownerPermits,
            int ownerCount,
            int maxAcceptedForSingleOwner,
            long wallDurationNanos,
            boolean capacityRestored
    ) {
    }
}