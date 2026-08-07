package com.leo.careerforgeai.agent.api.dto;

import com.leo.careerforgeai.agent.application.coach.CareerCoachResult;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentRunTrace;
import com.leo.careerforgeai.model.domain.ModelUsage;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 返回可信Career Coach回答、合法引用、脱敏工具摘要和总体运行指标。
 * @author: Miao Zheng
 * @date: 2026-08-07 06:20
 **/
public record CareerCoachResponse(
        String runId,
        CareerCoachAnswerStatus status,
        String answer,
        List<String> citedChunkIds,
        List<CareerCoachToolSummaryResponse> toolExecutions,
        int modelCallCount,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        long totalDurationMs
) {

    public CareerCoachResponse {
        Objects.requireNonNull(runId, "runId不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(answer, "answer不能为空");
        citedChunkIds = List.copyOf(citedChunkIds);
        toolExecutions = List.copyOf(toolExecutions);
    }

    /** 将可信业务结果映射为不暴露Prompt、消息历史和Tool Result的API响应。 */
    public static CareerCoachResponse from(CareerCoachResult result) {
        AgentRunTrace trace = result.trace();
        ModelUsage usage = trace.totalUsage();

        return new CareerCoachResponse(
                trace.runId(),
                result.answer().status(),
                result.answer().answer(),
                result.answer().citedChunkIds(),
                trace.toolCalls().stream()
                        .map(CareerCoachToolSummaryResponse::from)
                        .toList(),
                trace.modelCalls().size(),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens(),
                trace.durationMs()
        );
    }
}