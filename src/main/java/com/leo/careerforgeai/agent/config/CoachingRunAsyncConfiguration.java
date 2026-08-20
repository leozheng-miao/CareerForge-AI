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
 * @description: 创建Coaching Run专用虚拟线程执行器和Deadline调度器
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Configuration(proxyBeanMethods = false)
public class CoachingRunAsyncConfiguration {

    @Bean(destroyMethod = "close")
    public CoachingRunTaskExecutor coachingRunTaskExecutor(
            CoachingRunExecutionProperties properties,
            Clock agentClock
    ) {
        ExecutorService virtualThreadExecutor =
                Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual()
                                .name("careerforge-run-", 0)
                                .factory()
                );

        ScheduledExecutorService deadlineScheduler =
                Executors.newSingleThreadScheduledExecutor(
                        Thread.ofPlatform()
                                .daemon(true)
                                .name("careerforge-run-deadline-", 0)
                                .factory()
                );

        return new CoachingRunTaskExecutor(
                virtualThreadExecutor,
                deadlineScheduler,
                agentClock,
                properties.shutdownGracePeriod()
        );
    }
}