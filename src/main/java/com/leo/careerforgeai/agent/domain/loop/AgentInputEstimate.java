package com.leo.careerforgeai.agent.domain.loop;

/**
 * @program: CareerForge-AI
 * @description: 保存一次模型调用前估算的输入 Token 和模型可见字符数。
 * @author: Miao Zheng
 * @date: 2026-08-06 17:31
 **/
public record AgentInputEstimate(
        long estimatedInputTokens,
        int messageHistoryChars
) {

    public AgentInputEstimate {
        if (estimatedInputTokens < 0) throw new IllegalArgumentException("estimatedInputTokens 不能小于 0");
        if (messageHistoryChars < 0) throw new IllegalArgumentException("messageHistoryChars 不能小于 0");
    }
}