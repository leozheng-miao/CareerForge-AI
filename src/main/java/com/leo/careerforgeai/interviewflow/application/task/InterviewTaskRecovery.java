package com.leo.careerforgeai.interviewflow.application.task;

import com.leo.careerforgeai.interviewflow.config.InterviewTaskExecutorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 启动后持续回收可执行、重试到期和租约过期任务
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Component
@ConditionalOnExpression("${careerforge.persistence.enabled:false} && ${careerforge.interview-flow.task-executor.enabled:false}")
public final class InterviewTaskRecovery {

    private final InterviewTaskWorker worker;
    private final InterviewTaskExecutorProperties properties;

    public InterviewTaskRecovery(
            InterviewTaskWorker worker,
            InterviewTaskExecutorProperties properties
    ) {
        this.worker = Objects.requireNonNull(worker, "worker不能为空");
        this.properties = Objects.requireNonNull(
                properties,
                "properties不能为空"
        );
    }

    @Scheduled(
            fixedDelayString = "${careerforge.interview-flow.task-executor.poll-interval}"
    )
    public int recoverAvailableTasks() {
        return worker.pollAndDispatch(properties.pollBatchSize());
    }
}
