package com.leo.careerforgeai.interview.config;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @program: CareerForge-AI
 * @description: 创建与Graph执行线程和阶段五SSE线程隔离的面试SSE虚拟线程执行器
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class InterviewSseConfiguration {

    private static final ContextSnapshotFactory CONTEXT_SNAPSHOT_FACTORY = ContextSnapshotFactory.builder().clearMissing(true).build();

    @Bean(name = "interviewSseExecutor", destroyMethod = "shutdownNow")
    public ExecutorService interviewSseExecutor() {
        ExecutorService delegate = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("careerforge-interview-sse-", 0).factory());
        return ContextExecutorService.wrap(delegate, CONTEXT_SNAPSHOT_FACTORY);
    }
}