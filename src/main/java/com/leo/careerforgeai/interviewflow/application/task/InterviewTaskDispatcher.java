package com.leo.careerforgeai.interviewflow.application.task;

import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.ClaimedTask;
import com.leo.careerforgeai.interviewflow.config.InterviewTaskExecutorProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * @program: CareerForge-AI
 * @description: 以虚拟线程和双层准入限制执行面试后台任务
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Component
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public final class InterviewTaskDispatcher {

    private final InterviewTaskExecutorProperties properties;
    private final ExecutorService executor;
    private final Semaphore globalPermits;
    private final ConcurrentHashMap<String, Semaphore> ownerPermits =
            new ConcurrentHashMap<>();

    public InterviewTaskDispatcher(
            InterviewTaskExecutorProperties properties
    ) {
        this.properties = Objects.requireNonNull(
                properties,
                "properties不能为空"
        );
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.globalPermits = new Semaphore(
                properties.maxConcurrentTasks()
        );
    }

    public boolean tryDispatch(
            ClaimedTask task,
            Supplier<InterviewTaskWorker.TaskExecutionResult> execution,
            Runnable timeoutAction
    ) {
        Objects.requireNonNull(task, "task不能为空");
        Objects.requireNonNull(execution, "execution不能为空");
        Objects.requireNonNull(timeoutAction, "timeoutAction不能为空");
        Semaphore ownerPermit = ownerPermits.computeIfAbsent(
                task.ownerId().value(),
                ignored -> new Semaphore(
                        properties.maxConcurrentTasksPerOwner()
                )
        );
        if (!globalPermits.tryAcquire()) {
            return false;
        }
        if (!ownerPermit.tryAcquire()) {
            globalPermits.release();
            return false;
        }

        executor.submit(() -> runWithDeadline(
                execution,
                timeoutAction,
                ownerPermit
        ));
        return true;
    }

    private void runWithDeadline(
            Supplier<InterviewTaskWorker.TaskExecutionResult> execution,
            Runnable timeoutAction,
            Semaphore ownerPermit
    ) {
        Future<InterviewTaskWorker.TaskExecutionResult> future =
                executor.submit(execution::get);
        try {
            future.get(
                    properties.taskTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (TimeoutException exception) {
            future.cancel(true);
            timeoutAction.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
        } catch (java.util.concurrent.ExecutionException ignored) {
            // Worker已负责把执行异常收敛为任务状态。
        } finally {
            ownerPermit.release();
            globalPermits.release();
        }
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
