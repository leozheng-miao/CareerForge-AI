package com.leo.careerforgeai.agent.application.run.execution;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import io.micrometer.context.ContextSnapshotFactory;

/**
 * @program: CareerForge-AI
 * @description: 在专用虚拟线程执行Run并负责Deadline、中断、许可、MDC和关闭清理
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Slf4j
public final class CoachingRunTaskExecutor implements AutoCloseable {

    private final ExecutorService executorService;
    private final ScheduledExecutorService deadlineScheduler;
    private final Clock clock;
    private final Duration shutdownGracePeriod;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private static final ContextSnapshotFactory CONTEXT_SNAPSHOT_FACTORY = ContextSnapshotFactory.builder().clearMissing(true).build();

    public CoachingRunTaskExecutor(
            ExecutorService executorService,
            ScheduledExecutorService deadlineScheduler,
            Clock clock,
            Duration shutdownGracePeriod
    ) {
        this.executorService = Objects.requireNonNull(executorService, "executorService不能为空");
        this.deadlineScheduler = Objects.requireNonNull(deadlineScheduler, "deadlineScheduler不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
        this.shutdownGracePeriod = Objects.requireNonNull(shutdownGracePeriod, "shutdownGracePeriod不能为空");
        if (shutdownGracePeriod.isZero() || shutdownGracePeriod.isNegative()) {
            throw new IllegalArgumentException("shutdownGracePeriod必须大于0");
        }
    }

    public Future<?> submit(
            RunExecutionContext context,
            RunAdmissionLease lease,
            Consumer<RunExecutionContext> task
    ) {
        Objects.requireNonNull(context, "context不能为空");
        Objects.requireNonNull(lease, "lease不能为空");
        Objects.requireNonNull(task, "task不能为空");

        if (!context.ownerId().equals(lease.ownerId())) {
            lease.close();
            throw new IllegalArgumentException("执行上下文owner与许可owner不一致");
        }
        if (!accepting.get()) {
            lease.close();
            throw new RejectedExecutionException("Coaching Run执行器已经停止接收任务");
        }
        if (context.isExpired(clock.instant())) {
            lease.close();
            throw new RunExecutionDeadlineExceededException(
                    context.runId(),
                    context.deadline()
            );
        }

        Runnable propagatedTask = CONTEXT_SNAPSHOT_FACTORY.captureAll()
                .wrap(() -> executeTask(context, task));
        DeadlineFutureTask futureTask = new DeadlineFutureTask(propagatedTask, lease);

        try {
            executorService.execute(futureTask);
        } catch (RuntimeException exception) {
            lease.close();
            throw exception;
        }

        scheduleCancellation(context, futureTask);
        return futureTask;
    }

    public boolean isAccepting() {
        return accepting.get();
    }

    private void executeTask(
            RunExecutionContext context,
            Consumer<RunExecutionContext> task
    ) {
        if (context.isExpired(clock.instant())) {
            throw new RunExecutionDeadlineExceededException(
                    context.runId(),
                    context.deadline()
            );
        }

        RunMdcContext.from(context).run(() -> task.accept(context));
    }

    private void scheduleCancellation(
            RunExecutionContext context,
            DeadlineFutureTask futureTask
    ) {
        Duration remaining = Duration.between(
                clock.instant(),
                context.deadline()
        );

        if (remaining.isZero() || remaining.isNegative()) {
            futureTask.cancel(true);
            return;
        }

        ScheduledFuture<?> timeoutTask = deadlineScheduler.schedule(
                () -> {
                    if (futureTask.cancel(true)) {
                        log.warn(
                                "Coaching Run达到Deadline并请求中断，runId={}, deadline={}",
                                context.runId(),
                                context.deadline()
                        );
                    }
                },
                remaining.toNanos(),
                TimeUnit.NANOSECONDS
        );

        futureTask.registerTimeoutTask(timeoutTask);
    }

    @Override
    public void close() {
        if (!accepting.compareAndSet(true, false)) return;

        executorService.shutdown();

        try {
            if (!awaitExecutorTermination()) {
                cancelAbandonedTasks(executorService.shutdownNow());

                if (!awaitExecutorTermination()) {
                    log.warn(
                            "Coaching Run执行器在强制中断后仍未结束，gracePeriod={}",
                            shutdownGracePeriod
                    );
                }
            }
        } catch (InterruptedException exception) {
            cancelAbandonedTasks(executorService.shutdownNow());
            Thread.currentThread().interrupt();
        } finally {
            deadlineScheduler.shutdownNow();
        }
    }

    private boolean awaitExecutorTermination() throws InterruptedException {
        return executorService.awaitTermination(
                shutdownGracePeriod.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    private void cancelAbandonedTasks(List<Runnable> abandonedTasks) {
        for (Runnable abandonedTask : abandonedTasks) {
            if (abandonedTask instanceof Future<?> future) future.cancel(false);
        }
    }

    private static final class DeadlineFutureTask extends FutureTask<Void> {

        private final RunAdmissionLease lease;
        private final AtomicBoolean runEntered = new AtomicBoolean();
        private final AtomicReference<ScheduledFuture<?>> timeoutTask = new AtomicReference<>();

        private DeadlineFutureTask(Runnable task, RunAdmissionLease lease) {
            super(() -> {
                try {
                    task.run();
                } finally {
                    lease.close();
                }
            }, null);
            this.lease = lease;
        }

        @Override
        public void run() {
            runEntered.set(true);
            try {
                super.run();
            } finally {
                cancelTimeoutTask();
            }
        }

        @Override
        protected void done() {
            if (!runEntered.get()) {
                cancelTimeoutTask();
                lease.close();
            }
        }

        private void registerTimeoutTask(ScheduledFuture<?> scheduled) {
            timeoutTask.set(scheduled);
            if (isDone()) scheduled.cancel(false);
        }

        private void cancelTimeoutTask() {
            ScheduledFuture<?> scheduled = timeoutTask.getAndSet(null);
            if (scheduled != null) scheduled.cancel(false);
        }
    }
}