package com.leo.careerforgeai.agent.api.dto;

import com.leo.careerforgeai.agent.domain.loop.AgentToolCallTrace;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionErrorType;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;

/**
 * @program: CareerForge-AI
 * @description: 对外返回不包含参数、Tool Call ID和Tool Result的工具执行摘要。
 * @author: Miao Zheng
 * @date: 2026-08-07 06:20
 **/
public record CareerCoachToolSummaryResponse(
        int sequence,
        String toolName,
        ToolExecutionStatus status,
        long durationMs,
        Integer resultCount,
        ToolExecutionErrorType errorType
) {

    /** 将内部工具Trace转换为不包含敏感内容的API摘要。 */
    public static CareerCoachToolSummaryResponse from(AgentToolCallTrace trace) {
        return new CareerCoachToolSummaryResponse(
                trace.sequence(),
                trace.toolName(),
                trace.status(),
                trace.durationMs(),
                trace.resultCount(),
                trace.errorType()
        );
    }
}