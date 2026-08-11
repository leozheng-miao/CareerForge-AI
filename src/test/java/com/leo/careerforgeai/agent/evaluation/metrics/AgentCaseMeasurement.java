package com.leo.careerforgeai.agent.evaluation.metrics;

import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionErrorType;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存一次Agent评测Case运行产生的原始事实，不提前计算质量指标。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
public record AgentCaseMeasurement(
        String caseId,
        int runNumber,
        ExecutionMode executionMode,
        String runId,
        List<ToolCallMeasurement> toolCalls,
        AgentRunStatus runStatus,
        AgentTerminationReason terminationReason,
        FinalAnswerOutcome finalAnswerOutcome,
        CareerCoachAnswerStatus answerStatus,
        List<String> citedChunkIds,
        List<String> allowedCitationChunkIds,
        int modelIterations,
        long outerModelTokens,
        long toolModelTokens,
        long durationMs
) {

    private static final Pattern CASE_ID_PATTERN = Pattern.compile("agent-eval-[0-9]{3}");
    private static final Pattern CHUNK_ID_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public AgentCaseMeasurement {
        if (caseId == null || !CASE_ID_PATTERN.matcher(caseId).matches()) {
            throw new IllegalArgumentException("caseId格式不合法");
        }
        if (runNumber <= 0) throw new IllegalArgumentException("runNumber必须大于0");
        if (executionMode == null) throw new IllegalArgumentException("executionMode不能为空");
        requireText(runId, "runId");
        if (toolCalls == null || toolCalls.stream().anyMatch(toolCall -> toolCall == null)) {
            throw new IllegalArgumentException("toolCalls不能为空且不能包含空元素");
        }
        toolCalls = List.copyOf(toolCalls);
        if (runStatus == null) throw new IllegalArgumentException("runStatus不能为空");
        if (terminationReason == null) throw new IllegalArgumentException("terminationReason不能为空");
        if (finalAnswerOutcome == null) throw new IllegalArgumentException("finalAnswerOutcome不能为空");
        citedChunkIds = copyChunkIds(citedChunkIds, "citedChunkIds");
        allowedCitationChunkIds = copyChunkIds(allowedCitationChunkIds, "allowedCitationChunkIds");
        if (modelIterations < 0) throw new IllegalArgumentException("modelIterations不能小于0");
        if (outerModelTokens < 0 || toolModelTokens < 0) throw new IllegalArgumentException("Token不能小于0");
        if (durationMs < 0) throw new IllegalArgumentException("durationMs不能小于0");

        for (int index = 0; index < toolCalls.size(); index++) {
            ToolCallMeasurement toolCall = toolCalls.get(index);
            if (toolCall.sequence() != index + 1) throw new IllegalArgumentException("工具调用sequence必须从1连续递增");
            if (toolCall.modelIteration() > modelIterations) throw new IllegalArgumentException("工具调用迭代不能超过总模型迭代数");
        }

        validateFinalAnswer(runStatus, terminationReason, finalAnswerOutcome, answerStatus, citedChunkIds);
    }

    public int requestedToolCallCount() {
        return toolCalls.size();
    }

    public long validArgumentCallCount() {
        return toolCalls.stream().filter(ToolCallMeasurement::argumentsValid).count();
    }

    public List<String> actualTools() {
        return toolCalls.stream().map(ToolCallMeasurement::toolName).toList();
    }

    public long totalModelTokens() {
        try {
            return Math.addExact(outerModelTokens, toolModelTokens);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    public record ToolCallMeasurement(
            int sequence,
            int modelIteration,
            String toolCallId,
            String toolName,
            boolean argumentsValid,
            ToolAttemptOutcome outcome,
            ToolExecutionErrorType errorType
    ) {

        public ToolCallMeasurement {
            if (sequence <= 0) throw new IllegalArgumentException("sequence必须大于0");
            if (modelIteration <= 0) throw new IllegalArgumentException("modelIteration必须大于0");
            requireText(toolCallId, "toolCallId");
            requireText(toolName, "toolName");
            if (outcome == null) throw new IllegalArgumentException("outcome不能为空");
            if (outcome == ToolAttemptOutcome.FAILURE && errorType == null) {
                throw new IllegalArgumentException("失败工具调用必须包含errorType");
            }
            if (outcome != ToolAttemptOutcome.FAILURE && errorType != null) {
                throw new IllegalArgumentException("非失败工具调用不能包含errorType");
            }
        }
    }

    public enum ExecutionMode {
        STUB,
        REAL
    }

    public enum FinalAnswerOutcome {
        VALID,
        INVALID,
        NOT_PRODUCED
    }

    public enum ToolAttemptOutcome {
        SUCCESS,
        FAILURE,
        REJECTED_BY_LOOP
    }

    private static List<String> copyChunkIds(List<String> chunkIds, String fieldName) {
        if (chunkIds == null) throw new IllegalArgumentException(fieldName + "不能为空");
        if (chunkIds.stream().anyMatch(chunkId -> chunkId == null
                || !CHUNK_ID_PATTERN.matcher(chunkId).matches())) {
            throw new IllegalArgumentException(fieldName + "包含非法Chunk ID");
        }
        if (new HashSet<>(chunkIds).size() != chunkIds.size()) {
            throw new IllegalArgumentException(fieldName + "不能包含重复Chunk ID");
        }
        return List.copyOf(chunkIds);
    }

    private static void validateFinalAnswer(
            AgentRunStatus runStatus,
            AgentTerminationReason terminationReason,
            FinalAnswerOutcome finalAnswerOutcome,
            CareerCoachAnswerStatus answerStatus,
            List<String> citedChunkIds
    ) {
        boolean loopCompleted = runStatus == AgentRunStatus.COMPLETED
                && terminationReason == AgentTerminationReason.FINAL_ANSWER;

        if (loopCompleted && finalAnswerOutcome == FinalAnswerOutcome.NOT_PRODUCED) {
            throw new IllegalArgumentException("正常完成的Loop必须验证最终回答");
        }
        if (!loopCompleted && finalAnswerOutcome != FinalAnswerOutcome.NOT_PRODUCED) {
            throw new IllegalArgumentException("未正常完成的Loop不能产生最终回答");
        }
        if (finalAnswerOutcome == FinalAnswerOutcome.VALID && answerStatus == null) {
            throw new IllegalArgumentException("合法最终回答必须包含answerStatus");
        }
        if (finalAnswerOutcome != FinalAnswerOutcome.VALID && answerStatus != null) {
            throw new IllegalArgumentException("未通过验证的回答不能包含answerStatus");
        }
        if (finalAnswerOutcome != FinalAnswerOutcome.VALID && !citedChunkIds.isEmpty()) {
            throw new IllegalArgumentException("未通过验证的回答不能包含可信引用");
        }
        if (answerStatus != null && answerStatus != CareerCoachAnswerStatus.ANSWERED
                && !citedChunkIds.isEmpty()) {
            throw new IllegalArgumentException("非ANSWERED状态不能包含引用");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
    }
}