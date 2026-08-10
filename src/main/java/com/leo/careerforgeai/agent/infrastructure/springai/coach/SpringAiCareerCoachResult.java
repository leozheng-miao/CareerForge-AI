package com.leo.careerforgeai.agent.infrastructure.springai.coach;

import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswer;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 保存Spring AI Career Coach的可信回答及本轮可观测执行摘要。
 * @author: Miao Zheng
 * @date: 2026-08-10 02:00
 **/
public record SpringAiCareerCoachResult(
        CareerCoachAnswer answer,
        String runId,
        List<ToolExecutionResult> toolResults,
        long totalDurationMs
) {

    public SpringAiCareerCoachResult {
        Objects.requireNonNull(answer, "answer不能为空");
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId不能为空");
        if (toolResults == null || toolResults.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("toolResults不能为空且不能包含null");
        }
        toolResults = List.copyOf(toolResults);
        if (totalDurationMs < 0) throw new IllegalArgumentException("totalDurationMs不能小于0");
    }
}