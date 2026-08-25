package com.leo.careerforgeai.agent.config;

import com.leo.careerforgeai.agent.application.run.execution.CoachingRunTaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @program: CareerForge-AI
 * @description: 创建可对照的平台或虚拟线程Run执行器及Deadline调度器
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Configuration(proxyBeanMethods = false)
public class CoachingRunAsyncConfiguration {

    @Bean(destroyMethod = "close")
    public CoachingRunTaskExecutor coachingRunTaskExecutor(
            CoachingRunExecutionProperties runProperties,
            CoachingRunExecutorProperties executorProperties,
            Clock agentClock
    ) {
        ExecutorService runExecutor = createRunExecutor(executorProperties);
        ScheduledExecutorService deadlineScheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("careerforge-run-deadline-", 0).factory()
        );
        return new CoachingRunTaskExecutor(
                runExecutor,
                deadlineScheduler,
                agentClock,
                runProperties.shutdownGracePeriod()
        );
    }

    private ExecutorService createRunExecutor(CoachingRunExecutorProperties properties) {
        return switch (properties.mode()) {
            case VIRTUAL -> Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("careerforge-run-", 0).factory()
            );
            case PLATFORM -> Executors.newFixedThreadPool(
                    properties.platformThreadCount(),
                    Thread.ofPlatform().name("careerforge-run-platform-", 0).factory()
            );
        };
    }
}