package com.leo.careerforgeai.agent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @program: CareerForge-AI
 * @description: 创建与Run执行线程隔离的SSE观察虚拟线程执行器
 * @author: Miao Zheng
 * @date: 2026-08-21
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingRunSseConfiguration {

    @Bean(name = "coachingRunSseExecutor", destroyMethod = "shutdownNow")
    public ExecutorService coachingRunSseExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name("careerforge-run-sse-", 0)
                        .factory()
        );
    }
}