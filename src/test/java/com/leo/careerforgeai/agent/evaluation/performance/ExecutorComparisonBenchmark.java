package com.leo.careerforgeai.agent.evaluation.performance;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 使用相同固定阻塞负载对照平台线程池和虚拟线程执行器
 * @author: Miao Zheng
 * @date: 2026-08-25
 */
class ExecutorComparisonBenchmark {

    @Test
    void shouldMeasurePlatformAndVirtualThreadsUnderSameBlockingLoad() {
        int taskCount = Integer.getInteger(
                "careerforge.benchmark.tasks",
                500
        );
        int platformThreads = Integer.getInteger(
                "careerforge.benchmark.platform-threads",
                32
        );
        long blockingMillis = Long.getLong(
                "careerforge.benchmark.blocking-millis",
                50L
        );
        int repetitions = Integer.getInteger(
                "careerforge.benchmark.repetitions",
                3
        );

        FixedBlockingLoadProbe probe = new FixedBlockingLoadProbe();

        probe.run(
                ExecutionMode.PLATFORM,
                Math.min(taskCount, 100),
                platformThreads,
                Duration.ofMillis(5)
        );
        probe.run(
                ExecutionMode.VIRTUAL,
                Math.min(taskCount, 100),
                platformThreads,
                Duration.ofMillis(5)
        );

        for (int repetition = 1; repetition <= repetitions; repetition++) {
            List<ExecutionMode> order = repetition % 2 == 1
                    ? List.of(
                    ExecutionMode.PLATFORM,
                    ExecutionMode.VIRTUAL
            )
                    : List.of(
                    ExecutionMode.VIRTUAL,
                    ExecutionMode.PLATFORM
            );

            for (ExecutionMode mode : order) {
                LoadReport report = probe.run(
                        mode,
                        taskCount,
                        platformThreads,
                        Duration.ofMillis(blockingMillis)
                );

                assertThat(report.completedTasks())
                        .isEqualTo(taskCount);
                assertThat(report.failures()).isZero();
                assertThat(report.p50Nanos()).isPositive();
                assertThat(report.p95Nanos())
                        .isGreaterThanOrEqualTo(report.p50Nanos());
                assertThat(report.p99Nanos())
                        .isGreaterThanOrEqualTo(report.p95Nanos());

                printReport(repetition, report);
            }
        }
    }

    private void printReport(
            int repetition,
            LoadReport report
    ) {
        System.out.printf(
                Locale.ROOT,
                "executor=%s repetition=%d tasks=%d threadsCreated=%d "
                        + "peakActive=%d throughput=%.2f/s "
                        + "p50=%.2fms p95=%.2fms p99=%.2fms wall=%.2fms failures=%d%n",
                report.mode(),
                repetition,
                report.completedTasks(),
                report.createdThreads(),
                report.peakActiveTasks(),
                report.throughputPerSecond(),
                nanosToMillis(report.p50Nanos()),
                nanosToMillis(report.p95Nanos()),
                nanosToMillis(report.p99Nanos()),
                nanosToMillis(report.wallDurationNanos()),
                report.failures()
        );
    }

    private double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    /**
     * @program: CareerForge-AI
     * @description: 区分固定平台线程池与每任务一个虚拟线程的执行模式
     * @author: Miao Zheng
     * @date: 2026-08-25
     */
    private enum ExecutionMode {

        PLATFORM,
        VIRTUAL
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存一次固定阻塞负载的线程、吞吐和延迟测量结果
     * @author: Miao Zheng
     * @date: 2026-08-25
     * @param mode 执行器模式
     * @param submittedTasks 提交任务数
     * @param completedTasks 完成任务数
     * @param failures 失败任务数
     * @param createdThreads 创建线程数
     * @param peakActiveTasks 峰值同时执行任务数
     * @param wallDurationNanos 整体执行时间
     * @param throughputPerSecond 每秒完成任务数
     * @param p50Nanos 端到端p50延迟
     * @param p95Nanos 端到端p95延迟
     * @param p99Nanos 端到端p99延迟
     */
    private record LoadReport(
            ExecutionMode mode,
            int submittedTasks,
            int completedTasks,
            int failures,
            int createdThreads,
            int peakActiveTasks,
            long wallDurationNanos,
            double throughputPerSecond,
            long p50Nanos,
            long p95Nanos,
            long p99Nanos
    ) {
    }

    /**
     * @program: CareerForge-AI
     * @description: 在相同任务数量和阻塞时间下采集执行器端到端样本
     * @author: Miao Zheng
     * @date: 2026-08-25
     */
    private static final class FixedBlockingLoadProbe {

        private LoadReport run(
                ExecutionMode mode,
                int taskCount,
                int platformThreads,
                Duration blockingDuration
        ) {
            if (mode == null) {
                throw new IllegalArgumentException("mode不能为空");
            }
            if (taskCount < 1) {
                throw new IllegalArgumentException(
                        "taskCount必须大于0"
                );
            }
            if (platformThreads < 1) {
                throw new IllegalArgumentException(
                        "platformThreads必须大于0"
                );
            }
            if (blockingDuration == null
                    || blockingDuration.isZero()
                    || blockingDuration.isNegative()) {
                throw new IllegalArgumentException(
                        "blockingDuration必须大于0"
                );
            }

            AtomicInteger createdThreads = new AtomicInteger();
            AtomicInteger activeTasks = new AtomicInteger();
            AtomicInteger peakActiveTasks = new AtomicInteger();
            AtomicInteger failures = new AtomicInteger();
            AtomicLong releasedAt = new AtomicLong();
            ConcurrentLinkedQueue<Long> latencySamples =
                    new ConcurrentLinkedQueue<>();
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch completed = new CountDownLatch(taskCount);

            ThreadFactory baseFactory = mode == ExecutionMode.VIRTUAL
                    ? Thread.ofVirtual()
                    .name("benchmark-virtual-", 0)
                    .factory()
                    : Thread.ofPlatform()
                    .name("benchmark-platform-", 0)
                    .factory();

            ThreadFactory countingFactory = task -> {
                createdThreads.incrementAndGet();
                return baseFactory.newThread(task);
            };

            long finishedAt;

            try (ExecutorService executor = mode == ExecutionMode.VIRTUAL
                    ? Executors.newThreadPerTaskExecutor(countingFactory)
                    : Executors.newFixedThreadPool(
                    platformThreads,
                    countingFactory
            )) {
                for (int index = 0; index < taskCount; index++) {
                    executor.execute(() -> {
                        try {
                            startGate.await();

                            int active = activeTasks.incrementAndGet();
                            peakActiveTasks.accumulateAndGet(
                                    active,
                                    Math::max
                            );

                            try {
                                Thread.sleep(blockingDuration);
                            } finally {
                                activeTasks.decrementAndGet();
                            }
                        } catch (InterruptedException exception) {
                            failures.incrementAndGet();
                            Thread.currentThread().interrupt();
                        } catch (RuntimeException exception) {
                            failures.incrementAndGet();
                        } finally {
                            latencySamples.add(
                                    System.nanoTime()
                                            - releasedAt.get()
                            );
                            completed.countDown();
                        }
                    });
                }

                releasedAt.set(System.nanoTime());
                startGate.countDown();

                if (!completed.await(60, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "固定阻塞负载未在60秒内完成"
                    );
                }

                finishedAt = System.nanoTime();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "固定阻塞负载被中断",
                        exception
                );
            }

            List<Long> sortedSamples =
                    new ArrayList<>(latencySamples);
            sortedSamples.sort(Long::compareTo);

            long wallDuration = finishedAt - releasedAt.get();
            int completedTasks = sortedSamples.size();
            double wallSeconds =
                    wallDuration / 1_000_000_000.0;
            double throughput = completedTasks / wallSeconds;

            return new LoadReport(
                    mode,
                    taskCount,
                    completedTasks,
                    failures.get(),
                    createdThreads.get(),
                    peakActiveTasks.get(),
                    wallDuration,
                    throughput,
                    percentile(sortedSamples, 0.50),
                    percentile(sortedSamples, 0.95),
                    percentile(sortedSamples, 0.99)
            );
        }

        private long percentile(
                List<Long> sortedSamples,
                double percentile
        ) {
            if (sortedSamples.isEmpty()) {
                throw new IllegalArgumentException(
                        "延迟样本不能为空"
                );
            }

            int rank = (int) Math.ceil(
                    percentile * sortedSamples.size()
            );
            return sortedSamples.get(
                    Math.max(rank, 1) - 1
            );
        }
    }
}