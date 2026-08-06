package com.leo.careerforgeai.agent.domain.loop;

import java.time.Duration;

/**
 * @program: CareerForge-AI
 * @description: 定义单次 Agent Run 的迭代、工具、Token、消息和超时限制。
 * @author: Miao Zheng
 * @date: 2026-08-06 17:00
 */
public record AgentLoopPolicy(
        int maxModelIterations,
        int maxTotalToolCalls,
        int maxCallsPerTool,
        int maxRepeatedCallCount,
        long maxTotalTokens,
        int maxOutputTokensPerModelCall,
        int maxMessageHistoryChars,
        Duration totalTimeout,
        Duration modelCallTimeout
) {

    public AgentLoopPolicy {
        if (maxModelIterations <= 0) throw new IllegalArgumentException("maxModelIterations 必须大于 0");
        if (maxTotalToolCalls <= 0) throw new IllegalArgumentException("maxTotalToolCalls 必须大于 0");
        if (maxCallsPerTool <= 0 || maxCallsPerTool > maxTotalToolCalls) {
            throw new IllegalArgumentException("maxCallsPerTool 必须大于 0 且不能超过 maxTotalToolCalls");
        }
        if (maxRepeatedCallCount < 2 || maxRepeatedCallCount > maxCallsPerTool) {
            throw new IllegalArgumentException("maxRepeatedCallCount 必须在 2 到 maxCallsPerTool 之间");
        }
        if (maxTotalTokens <= 0) throw new IllegalArgumentException("maxTotalTokens 必须大于 0");
        if (maxOutputTokensPerModelCall <= 0 || maxOutputTokensPerModelCall >= maxTotalTokens) {
            throw new IllegalArgumentException("maxOutputTokensPerModelCall 必须大于 0 且小于 maxTotalTokens");
        }
        if (maxMessageHistoryChars <= 0) throw new IllegalArgumentException("maxMessageHistoryChars 必须大于 0");
        if (totalTimeout == null || totalTimeout.isZero() || totalTimeout.isNegative()) {
            throw new IllegalArgumentException("totalTimeout 必须大于 0");
        }
        if (modelCallTimeout == null || modelCallTimeout.isZero() || modelCallTimeout.isNegative()) {
            throw new IllegalArgumentException("modelCallTimeout 必须大于 0");
        }
        if (modelCallTimeout.compareTo(totalTimeout) > 0) {
            throw new IllegalArgumentException("modelCallTimeout 不能超过 totalTimeout");
        }
    }
}