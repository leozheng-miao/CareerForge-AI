package com.leo.careerforgeai.agent.config;

import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.application.tool.ToolRegistry;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.adapter.SpringAiToolCallbackCatalog;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.adapter.SpringAiToolDefinitionAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @program: CareerForge-AI
 * @description: 显式装配Spring AI工具定义适配器和受控ToolCallback目录。
 * @author: Miao Zheng
 * @date: 2026-08-07 20:10
 **/
@Configuration(proxyBeanMethods = false)
public class SpringAiToolConfiguration {

    @Bean
    public SpringAiToolDefinitionAdapter springAiToolDefinitionAdapter() {
        return new SpringAiToolDefinitionAdapter();
    }

    @Bean
    public SpringAiToolCallbackCatalog springAiToolCallbackCatalog(
            ToolRegistry toolRegistry,
            SpringAiToolDefinitionAdapter definitionAdapter,
            SafeToolExecutor safeToolExecutor
    ) {
        return new SpringAiToolCallbackCatalog(toolRegistry, definitionAdapter, safeToolExecutor);
    }
}