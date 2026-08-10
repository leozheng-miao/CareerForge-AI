package com.leo.careerforgeai.agent.infrastructure.springai.coach;

import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;

import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 将Spring AI内部执行异常转换为不泄露供应商详情的稳定失败语义。
 * @author: Miao Zheng
 * @date: 2026-08-10 03:40
 **/
public final class SpringAiCareerCoachExecutionException extends RuntimeException {

    private final String runId;
    private final SpringAiCareerCoachErrorType errorType;
    private final List<ToolExecutionResult> toolResults;

    public SpringAiCareerCoachExecutionException(
            String runId,
            SpringAiCareerCoachErrorType errorType,
            List<ToolExecutionResult> toolResults,
            Throwable cause
    ) {
        super("Spring AI Career Coach未能完成本次请求", Objects.requireNonNull(cause, "cause不能为空"));
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId不能为空");
        this.runId = runId;
        this.errorType = Objects.requireNonNull(errorType, "errorType不能为空");
        if (toolResults == null || toolResults.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("toolResults不能为空且不能包含null");
        }
        this.toolResults = List.copyOf(toolResults);
    }

    public String getRunId() {
        return runId;
    }

    public SpringAiCareerCoachErrorType getErrorType() {
        return errorType;
    }

    public List<ToolExecutionResult> getToolResults() {
        return toolResults;
    }
}