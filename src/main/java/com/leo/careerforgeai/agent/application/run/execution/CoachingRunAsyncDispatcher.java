package com.leo.careerforgeai.agent.application.run.execution;

import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @program: CareerForge-AI
 * @description: 衔接Run容量准入、许可移交和专用虚拟线程任务提交
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Component
public class CoachingRunAsyncDispatcher {

    private final CoachingRunAdmissionGate admissionGate;
    private final CoachingRunTaskExecutor taskExecutor;

    public CoachingRunAsyncDispatcher(
            CoachingRunAdmissionGate admissionGate,
            CoachingRunTaskExecutor taskExecutor
    ) {
        this.admissionGate = Objects.requireNonNull(admissionGate, "admissionGate不能为空");
        this.taskExecutor = Objects.requireNonNull(taskExecutor, "taskExecutor不能为空");
    }

    public RunAdmissionLease acquire(ActorId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        return admissionGate.tryAcquire(ownerId)
                .orElseThrow(() -> new CoachingRunCapacityRejectedException(ownerId));
    }

    public Future<?> dispatch(
            RunExecutionContext context,
            Consumer<RunExecutionContext> task
    ) {
        Objects.requireNonNull(context, "context不能为空");
        Objects.requireNonNull(task, "task不能为空");
        return dispatch(context, acquire(context.ownerId()), task);
    }

    public Future<?> dispatch(
            RunExecutionContext context,
            RunAdmissionLease lease,
            Consumer<RunExecutionContext> task
    ) {
        Objects.requireNonNull(lease, "lease不能为空");

        try {
            Objects.requireNonNull(context, "context不能为空");
            Objects.requireNonNull(task, "task不能为空");
            return taskExecutor.submit(context, lease, task);
        } catch (RuntimeException | Error exception) {
            lease.close();
            throw exception;
        }
    }
}