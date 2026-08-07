package com.leo.careerforgeai.model.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @program: CareerForge-AI
 * @description: 为模型流式响应配置独立且有界的异步任务执行器。
 * @author: Miao Zheng
 * @date: 2026-08-07 12:25
 **/
@Configuration(proxyBeanMethods = false)
public class ModelAsyncConfiguration {

    /** 创建供模型SSE流式接口使用的有界任务执行器。 */
    @Bean(name = "modelStreamTaskExecutor")
    public ThreadPoolTaskExecutor modelStreamTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("careerforge-model-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}