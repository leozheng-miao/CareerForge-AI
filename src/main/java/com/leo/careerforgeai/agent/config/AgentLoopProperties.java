package com.leo.careerforgeai.agent.config;

import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @program: CareerForge-AI
 * @description: 绑定并校验服务端控制的Agent Loop预算、超时和工具线程池配置。
 * @author: Miao Zheng
 * @date: 2026-08-07 04:10
 **/
@Validated
@ConfigurationProperties(prefix = "careerforge.agent.loop", ignoreUnknownFields = false)
public record AgentLoopProperties(
        int maxModelIterations,
        int maxTotalToolCalls,
        int maxCallsPerTool,
        int maxRepeatedCallCount,
        long maxTotalTokens,
        int maxOutputTokensPerModelCall,
        int maxMessageHistoryChars,
        Duration totalTimeout,
        Duration modelCallTimeout,
        int toolExecutorThreads,
        int toolExecutorQueueCapacity
) {

    private static final int MAX_TOOL_EXECUTOR_THREADS = 64;
    private static final int MAX_TOOL_EXECUTOR_QUEUE_CAPACITY = 10_000;

    public AgentLoopProperties {
        new AgentLoopPolicy(maxModelIterations, maxTotalToolCalls, maxCallsPerTool, maxRepeatedCallCount,
                maxTotalTokens, maxOutputTokensPerModelCall, maxMessageHistoryChars, totalTimeout, modelCallTimeout);

        if (toolExecutorThreads <= 0 || toolExecutorThreads > MAX_TOOL_EXECUTOR_THREADS) {
            throw new IllegalArgumentException("toolExecutorThreads必须在1到64之间");
        }
        if (toolExecutorQueueCapacity <= 0 || toolExecutorQueueCapacity > MAX_TOOL_EXECUTOR_QUEUE_CAPACITY) {
            throw new IllegalArgumentException("toolExecutorQueueCapacity必须在1到10000之间");
        }
    }

    /** 将外部配置转换为Agent Loop使用的不可变策略。 */
    public AgentLoopPolicy toPolicy() {
        return new AgentLoopPolicy(maxModelIterations, maxTotalToolCalls, maxCallsPerTool, maxRepeatedCallCount,
                maxTotalTokens, maxOutputTokensPerModelCall, maxMessageHistoryChars, totalTimeout, modelCallTimeout);
    }
}