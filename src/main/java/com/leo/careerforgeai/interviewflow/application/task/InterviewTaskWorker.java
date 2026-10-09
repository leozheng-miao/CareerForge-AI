package com.leo.careerforgeai.interviewflow.application.task;

import com.leo.careerforgeai.interviewflow.application.port.InterviewEventPublisher;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.ClaimedTask;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskType;
import com.leo.careerforgeai.interviewflow.config.InterviewTaskExecutorProperties;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 认领并执行可恢复面试任务，拒绝陈旧版本和重复完成
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class InterviewTaskWorker {

    private static final String ERROR_STALE = "STALE_RESULT";
    private static final String ERROR_EXECUTION = "TASK_EXECUTION_FAILED";
    private static final String ERROR_TIMEOUT = "TASK_TIMEOUT";
    private static final String ERROR_CAPACITY = "CAPACITY_REJECTED";

    private final InterviewTaskRepository taskRepository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewTaskHandlerRegistry handlerRegistry;
    private final InterviewTaskDispatcher dispatcher;
    private final InterviewTaskExecutorProperties properties;
    private final InterviewEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public InterviewTaskWorker(
            InterviewTaskRepository taskRepository,
            InterviewSessionRepository sessionRepository,
            InterviewTaskHandlerRegistry handlerRegistry,
            InterviewTaskDispatcher dispatcher,
            InterviewTaskExecutorProperties properties,
            InterviewEventPublisher eventPublisher,
            Clock clock
    ) {
        this.taskRepository = Objects.requireNonNull(
                taskRepository,
                "taskRepository不能为空"
        );
        this.sessionRepository = Objects.requireNonNull(
                sessionRepository,
                "sessionRepository不能为空"
        );
        this.handlerRegistry = Objects.requireNonNull(
                handlerRegistry,
                "handlerRegistry不能为空"
        );
        this.dispatcher = Objects.requireNonNull(
                dispatcher,
                "dispatcher不能为空"
        );
        this.properties = Objects.requireNonNull(
                properties,
                "properties不能为空"
        );
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    public InterviewTaskWorker(
            InterviewTaskRepository taskRepository,
            InterviewSessionRepository sessionRepository,
            InterviewTaskHandlerRegistry handlerRegistry,
            InterviewTaskDispatcher dispatcher,
            InterviewTaskExecutorProperties properties,
            Clock clock
    ) {
        this(taskRepository, sessionRepository, handlerRegistry, dispatcher,
                properties, new NoOpEventPublisher(), clock);
    }

    public int pollAndDispatch(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit必须在1到100之间");
        }
        int claimedCount = 0;
        for (int index = 0; index < limit; index++) {
            Instant now = clock.instant();
            String leaseOwner = "interview-worker-" + UUID.randomUUID();
            var claimed = taskRepository.claimNextAvailableTask(
                    now,
                    leaseOwner,
                    now.plus(properties.leaseDuration())
            );
            if (claimed.isEmpty()) {
                break;
            }
            ClaimedTask task = claimed.get();
            claimedCount++;
            boolean accepted = dispatcher.tryDispatch(
                    task,
                    () -> execute(task),
                    () -> timeout(task)
            );
            if (!accepted) {
                rescheduleUnstarted(
                        task,
                        ERROR_CAPACITY,
                        "面试任务执行容量已满"
                );
            }
        }
        return claimedCount;
    }

    public TaskExecutionResult execute(ClaimedTask task) {
        Objects.requireNonNull(task, "task不能为空");
        if (!isCurrent(task)) {
            return obsolete(task);
        }
        try {
            handlerRegistry.require(task.taskType()).handle(task);
            if (!isCurrent(task)) {
                return obsolete(task);
            }
            publishProgress(task);
            boolean completed = taskRepository.markSucceeded(
                    task,
                    clock.instant()
            );
            return completed
                    ? new TaskExecutionResult(
                            task.taskId(),
                            ExecutionStatus.SUCCEEDED,
                            null
                    )
                    : new TaskExecutionResult(
                            task.taskId(),
                            ExecutionStatus.LOST_LEASE,
                            "LOST_LEASE"
                    );
        } catch (RuntimeException exception) {
            return retryOrFail(
                    task,
                    ERROR_EXECUTION,
                    "任务处理失败: " + exception.getClass().getSimpleName()
            );
        }
    }

    public TaskExecutionResult timeout(ClaimedTask task) {
        return retryOrFail(task, ERROR_TIMEOUT, "任务执行超过截止时间");
    }

    private boolean isCurrent(ClaimedTask task) {
        return sessionRepository.findVersions(
                        task.ownerId(),
                        task.interviewId()
                )
                .filter(versions -> versions.inputVersion()
                        == task.inputVersion())
                .filter(versions -> versions.planVersion()
                        == task.planVersion())
                .isPresent();
    }

    private TaskExecutionResult obsolete(ClaimedTask task) {
        taskRepository.markFailed(
                task,
                ERROR_STALE,
                "任务输入或计划版本已经过期",
                clock.instant()
        );
        return new TaskExecutionResult(
                task.taskId(),
                ExecutionStatus.OBSOLETE,
                ERROR_STALE
        );
    }

    private TaskExecutionResult retryOrFail(
            ClaimedTask task,
            String errorCode,
            String errorMessage
    ) {
        Instant now = clock.instant();
        if (task.attemptCount() < task.maxAttempts()) {
            boolean updated = taskRepository.markRetryWait(
                    task,
                    now.plus(properties.retryDelay()),
                    errorCode,
                    errorMessage,
                    now
            );
            return new TaskExecutionResult(
                    task.taskId(),
                    updated
                            ? ExecutionStatus.RETRY_WAIT
                            : ExecutionStatus.LOST_LEASE,
                    errorCode
            );
        }
        boolean updated = taskRepository.markFailed(
                task,
                errorCode,
                errorMessage,
                now
        );
        if (updated) {
            handlerRegistry.require(task.taskType())
                    .onTerminalFailure(task, errorCode);
        }
        return new TaskExecutionResult(
                task.taskId(),
                updated
                        ? ExecutionStatus.FAILED
                        : ExecutionStatus.LOST_LEASE,
                errorCode
        );
    }

    private TaskExecutionResult rescheduleUnstarted(
            ClaimedTask task,
            String errorCode,
            String errorMessage
    ) {
        Instant now = clock.instant();
        boolean updated = taskRepository.rescheduleUnstarted(
                task,
                now.plus(properties.retryDelay()),
                errorCode,
                errorMessage,
                now
        );
        return new TaskExecutionResult(
                task.taskId(),
                updated
                        ? ExecutionStatus.RETRY_WAIT
                        : ExecutionStatus.LOST_LEASE,
                errorCode
        );
    }

    private void publishProgress(ClaimedTask task) {
        if (eventPublisher instanceof NoOpEventPublisher) {
            return;
        }
        sessionRepository.findById(task.ownerId(), task.interviewId())
                .ifPresent(aggregate -> {
                    InterviewEventPublisher.EventType type = switch (task.taskType()) {
                        case PREPARE_INTERVIEW -> aggregate.session().stage()
                                == SessionStage.READY
                                ? InterviewEventPublisher.EventType.PREPARATION_READY
                                : null;
                        case ANALYZE_TURN -> aggregate.session().stage()
                                == SessionStage.INTERVIEWING
                                ? InterviewEventPublisher.EventType.QUESTION_READY
                                : null;
                        case GENERATE_REPORT -> null;
                    };
                    if (type == null) {
                        return;
                    }
                    try {
                        eventPublisher.publish(
                                new InterviewEventPublisher.SafeInterviewEvent(
                                        UUID.randomUUID().toString(),
                                        task.interviewId(),
                                        task.ownerId(),
                                        type,
                                        null,
                                        aggregate.session().stage().name(),
                                        aggregate.session().version(),
                                        clock.instant()
                                )
                        );
                    } catch (RuntimeException ignored) {
                        // 事件是辅助通道，失败不重放已提交的业务处理。
                    }
                });
    }

    private static final class NoOpEventPublisher
            implements InterviewEventPublisher {
        @Override
        public void publish(SafeInterviewEvent event) {
        }

        @Override
        public java.util.List<SafeInterviewEvent> replay(
                com.leo.careerforgeai.shared.actor.ActorId ownerId,
                UUID interviewId,
                String afterEventId,
                int limit
        ) {
            return java.util.List.of();
        }

        @Override
        public AutoCloseable subscribe(
                com.leo.careerforgeai.shared.actor.ActorId ownerId,
                UUID interviewId,
                java.util.function.Consumer<SafeInterviewEvent> consumer
        ) {
            return () -> { };
        }
    }

    public record TaskExecutionResult(
            UUID taskId,
            ExecutionStatus status,
            String errorCode
    ) {

        public TaskExecutionResult {
            Objects.requireNonNull(taskId, "taskId不能为空");
            Objects.requireNonNull(status, "status不能为空");
        }
    }

    public enum ExecutionStatus {
        SUCCEEDED,
        RETRY_WAIT,
        FAILED,
        OBSOLETE,
        LOST_LEASE
    }
}
