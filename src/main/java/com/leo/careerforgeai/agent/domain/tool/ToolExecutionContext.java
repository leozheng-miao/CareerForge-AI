package com.leo.careerforgeai.agent.domain.tool;

import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;

import java.time.Instant;
import java.util.Objects;

/** 保存工具执行时由服务端提供、模型无法覆盖的受信任上下文。 */
public record ToolExecutionContext(
        String agentRunId,
        Instant deadline,
        RetrievalScope retrievalScope
) {

    public ToolExecutionContext {
        if (agentRunId == null || agentRunId.isBlank()) {
            throw new IllegalArgumentException("agentRunId 不能为空");
        }
        Objects.requireNonNull(deadline, "deadline 不能为空");
        Objects.requireNonNull(retrievalScope, "retrievalScope 不能为空");
    }
}