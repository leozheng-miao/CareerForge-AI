package com.leo.careerforgeai.agent.config;

import com.leo.careerforgeai.agent.application.loop.AgentLoop;
import com.leo.careerforgeai.agent.application.loop.AgentTokenEstimator;
import com.leo.careerforgeai.agent.application.loop.HeuristicAgentTokenEstimator;
import com.leo.careerforgeai.agent.application.loop.ToolCallFingerprintService;
import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.application.tool.ToolRegistry;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.model.application.ToolCallingGateway;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import io.micrometer.observation.ObservationRegistry;

/**
 * @program: CareerForge-AI
 * @description: 装配Agent Loop、工具安全执行器、预算组件和有界工具线程池。
 * @author: Miao Zheng
 * @date: 2026-08-07 04:30
 **/
@Configuration(proxyBeanMethods = false)
public class AgentRuntimeConfiguration {

    /** 创建用于Deadline计算且便于测试替换的UTC时钟。 */
    @Bean
    public Clock agentClock() {
        return Clock.systemUTC();
    }

    /** 创建线程数和等待队列均受限的工具执行线程池。 */
    @Bean(name = "agentToolExecutorService", destroyMethod = "shutdownNow")
    public ExecutorService agentToolExecutorService(AgentLoopProperties properties) {
        return new ThreadPoolExecutor(
                properties.toolExecutorThreads(),
                properties.toolExecutorThreads(),
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.toolExecutorQueueCapacity()),
                Thread.ofPlatform().name("careerforge-agent-tool-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /** 创建调用前使用的启发式Token估算器。 */
    @Bean
    public AgentTokenEstimator agentTokenEstimator() {
        return new HeuristicAgentTokenEstimator();
    }

    /** 创建基于规范化参数和上下文版本的工具调用指纹服务。 */
    @Bean
    public ToolCallFingerprintService toolCallFingerprintService(JsonMapper jsonMapper) {
        return new ToolCallFingerprintService(jsonMapper);
    }

    /** 创建服务端控制的不可变Agent Loop策略。 */
    @Bean
    public AgentLoopPolicy agentLoopPolicy(AgentLoopProperties properties) {
        return properties.toPolicy();
    }

    /** 创建负责白名单、参数、超时和输出限制的工具安全执行器。 */
    @Bean
    public SafeToolExecutor safeToolExecutor(
            ToolRegistry toolRegistry,
            JsonMapper jsonMapper,
            Validator validator,
            @Qualifier("agentToolExecutorService") ExecutorService executorService,
            Clock agentClock,
            ObservationRegistry observationRegistry
    ) {
        return new SafeToolExecutor(
                toolRegistry, jsonMapper, validator, executorService, agentClock, observationRegistry);
    }

    /** 创建使用原生Tool Calling Gateway的手写Agent Loop。 */
    @Bean
    public AgentLoop agentLoop(
            ToolCallingGateway toolCallingGateway,
            ToolRegistry toolRegistry,
            SafeToolExecutor safeToolExecutor,
            AgentTokenEstimator agentTokenEstimator,
            ToolCallFingerprintService fingerprintService,
            AgentLoopPolicy policy,
            Clock agentClock
    ) {
        return new AgentLoop(toolCallingGateway, toolRegistry, safeToolExecutor,
                agentTokenEstimator, fingerprintService, policy, agentClock);
    }
}