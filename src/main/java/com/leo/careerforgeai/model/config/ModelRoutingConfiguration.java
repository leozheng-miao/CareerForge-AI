package com.leo.careerforgeai.model.config;

import com.leo.careerforgeai.model.application.routing.TaskAwareModelRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @program: CareerForge-AI
 * @description: 创建经过启动期校验的模型任务路由器。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
@Configuration(proxyBeanMethods = false)
public class ModelRoutingConfiguration {

    @Bean
    public TaskAwareModelRouter taskAwareModelRouter(ModelRoutingProperties properties) {
        return new TaskAwareModelRouter(properties.executionRoutes(), properties.version());
    }
}