package com.leo.careerforgeai.agent.evaluation.metrics;

import com.leo.careerforgeai.agent.domain.tool.ToolExecutionErrorType;
import com.leo.careerforgeai.agent.evaluation.dataset.AgentEvaluationDataset;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 将固定Case标注与单次运行事实比较，生成可聚合的单Case指标。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
public final class AgentCaseMetricsCalculator {

    public AgentCaseMetrics calculate(
            AgentEvaluationDataset.EvaluationCase expected,
            AgentCaseMeasurement actual
    ) {
        if (expected == null) throw new IllegalArgumentException("expected不能为空");
        if (actual == null) throw new IllegalArgumentException("actual不能为空");
        if (!expected.caseId().equals(actual.caseId())) throw new IllegalArgumentException("Case标注与实测记录不匹配");

        List<String> actualTools = actual.actualTools();
        Set<String> expectedToolSet = new HashSet<>(expected.expectedTools());
        int requiredToolHits = (int) expected.expectedTools().stream().filter(actualTools::contains).count();
        int unnecessaryToolCalls = countUnnecessaryToolCalls(expected, actualTools);
        int requestedToolCalls = actual.requestedToolCallCount();
        int validArgumentCalls = Math.toIntExact(actual.validArgumentCallCount());
        int executedToolCalls = (int) actual.toolCalls().stream()
                .filter(toolCall -> toolCall.outcome() != AgentCaseMeasurement.ToolAttemptOutcome.REJECTED_BY_LOOP)
                .count();
        boolean requiredToolsComplete = requiredToolHits == expected.expectedTools().size();
        boolean toolExecutionsWithinLimit = executedToolCalls <= expected.maxToolCalls();
        long rejectedByLoopCalls = actual.toolCalls().stream()
                .filter(toolCall -> toolCall.outcome() == AgentCaseMeasurement.ToolAttemptOutcome.REJECTED_BY_LOOP)
                .count();
        boolean expectedLoopRejectionHandled =
                expected.faultMode() == AgentEvaluationDataset.FaultMode.REPEATED_IDENTICAL_CALL
                        && rejectedByLoopCalls == 1
                        && unnecessaryToolCalls == 1;
        boolean unnecessaryBehaviorMatched =
                unnecessaryToolCalls == 0 || expectedLoopRejectionHandled;
        boolean toolSelectionMatched =
                requiredToolsComplete
                        && unnecessaryBehaviorMatched
                        && toolExecutionsWithinLimit;
        boolean sequenceApplicable = expected.sequenceMode() == AgentEvaluationDataset.SequenceMode.EXACT_ORDER;
        boolean sequenceCorrect = !sequenceApplicable || actualRequiredToolOrder(actualTools, expectedToolSet)
                .equals(expected.expectedOrder());
        boolean argumentBehaviorMatched = argumentBehaviorMatched(expected, actual);
        boolean outcomeMatched = outcomeMatched(expected, actual);
        boolean citationApplicable = expected.requiredCitation() || !actual.citedChunkIds().isEmpty();
        boolean citationLegal = citationLegal(expected, actual);
        boolean loopTerminatedAsExpected = actual.runStatus() == expected.expectedRunStatus()
                && actual.terminationReason() == expected.expectedTerminationReason()
                && toolExecutionsWithinLimit;
        boolean toolFailureRecoveryApplicable = expected.faultMode() == AgentEvaluationDataset.FaultMode.TOOL_SYSTEM_ERROR
                || expected.faultMode() == AgentEvaluationDataset.FaultMode.TOOL_TIMEOUT;
        boolean toolFailureRecovered = toolFailureRecoveryApplicable
                && expectedToolFailureObserved(expected, actual)
                && outcomeMatched;

        boolean taskSucceeded = toolSelectionMatched
                && sequenceCorrect
                && argumentBehaviorMatched
                && outcomeMatched
                && citationLegal;

        return new AgentCaseMetrics(
                expected.caseId(),
                actual.runNumber(),
                actual.executionMode(),
                requiredToolHits,
                expected.expectedTools().size(),
                unnecessaryToolCalls,
                requestedToolCalls,
                validArgumentCalls,
                executedToolCalls,
                sequenceApplicable,
                sequenceCorrect,
                argumentBehaviorMatched,
                outcomeMatched,
                citationApplicable,
                citationLegal,
                loopTerminatedAsExpected,
                toolFailureRecoveryApplicable,
                toolFailureRecovered,
                taskSucceeded,
                actual.modelIterations(),
                actual.outerModelTokens(),
                actual.toolModelTokens(),
                actual.durationMs()
        );
    }

    private int countUnnecessaryToolCalls(
            AgentEvaluationDataset.EvaluationCase expected,
            List<String> actualTools
    ) {
        int acceptedExpectedCalls = 0;
        int unnecessaryCalls = 0;
        for (String actualTool : actualTools) {
            if (expected.expectedTools().contains(actualTool)
                    && acceptedExpectedCalls < expected.maxToolCalls()) {
                acceptedExpectedCalls++;
            } else {
                unnecessaryCalls++;
            }
        }
        return unnecessaryCalls;
    }

    private List<String> actualRequiredToolOrder(List<String> actualTools, Set<String> expectedTools) {
        return actualTools.stream().filter(expectedTools::contains).distinct().toList();
    }

    private boolean argumentBehaviorMatched(
            AgentEvaluationDataset.EvaluationCase expected,
            AgentCaseMeasurement actual
    ) {
        if (expected.faultMode() != AgentEvaluationDataset.FaultMode.INVALID_ARGUMENTS_ONCE) {
            return actual.toolCalls().stream().allMatch(AgentCaseMeasurement.ToolCallMeasurement::argumentsValid);
        }

        long invalidCalls = actual.toolCalls().stream()
                .filter(toolCall -> !toolCall.argumentsValid())
                .count();
        boolean expectedErrorObserved = actual.toolCalls().stream()
                .anyMatch(toolCall -> !toolCall.argumentsValid()
                        && toolCall.outcome() == AgentCaseMeasurement.ToolAttemptOutcome.FAILURE
                        && (toolCall.errorType() == ToolExecutionErrorType.INVALID_ARGUMENTS
                        || toolCall.errorType() == ToolExecutionErrorType.VALIDATION_FAILED));
        boolean validRecoveryObserved = actual.toolCalls().stream()
                .anyMatch(toolCall -> toolCall.argumentsValid()
                        && expected.expectedTools().contains(toolCall.toolName()));

        return invalidCalls == 1 && expectedErrorObserved && validRecoveryObserved;
    }

    private boolean outcomeMatched(
            AgentEvaluationDataset.EvaluationCase expected,
            AgentCaseMeasurement actual
    ) {
        if (actual.runStatus() != expected.expectedRunStatus()
                || actual.terminationReason() != expected.expectedTerminationReason()) {
            return false;
        }
        if (expected.expectedAnswerStatus() == null) {
            return actual.finalAnswerOutcome() == AgentCaseMeasurement.FinalAnswerOutcome.NOT_PRODUCED
                    && actual.answerStatus() == null;
        }
        return actual.finalAnswerOutcome() == AgentCaseMeasurement.FinalAnswerOutcome.VALID
                && actual.answerStatus() == expected.expectedAnswerStatus();
    }

    private boolean citationLegal(
            AgentEvaluationDataset.EvaluationCase expected,
            AgentCaseMeasurement actual
    ) {
        if (!expected.requiredCitation()) return actual.citedChunkIds().isEmpty();
        return !actual.citedChunkIds().isEmpty()
                && new HashSet<>(actual.allowedCitationChunkIds()).containsAll(actual.citedChunkIds());
    }

    private boolean expectedToolFailureObserved(
            AgentEvaluationDataset.EvaluationCase expected,
            AgentCaseMeasurement actual
    ) {
        ToolExecutionErrorType expectedError = expected.faultMode() == AgentEvaluationDataset.FaultMode.TOOL_TIMEOUT
                ? ToolExecutionErrorType.TIMEOUT
                : ToolExecutionErrorType.EXECUTION_FAILED;
        return actual.toolCalls().stream()
                .anyMatch(toolCall -> toolCall.outcome() == AgentCaseMeasurement.ToolAttemptOutcome.FAILURE
                        && toolCall.errorType() == expectedError);
    }

    public record AgentCaseMetrics(
            String caseId,
            int runNumber,
            AgentCaseMeasurement.ExecutionMode executionMode,
            int requiredToolHits,
            int requiredToolCount,
            int unnecessaryToolCalls,
            int requestedToolCalls,
            int validArgumentCalls,
            int executedToolCalls,
            boolean sequenceApplicable,
            boolean sequenceCorrect,
            boolean argumentBehaviorMatched,
            boolean outcomeMatched,
            boolean citationApplicable,
            boolean citationLegal,
            boolean loopTerminatedAsExpected,
            boolean toolFailureRecoveryApplicable,
            boolean toolFailureRecovered,
            boolean taskSucceeded,
            int modelIterations,
            long outerModelTokens,
            long toolModelTokens,
            long durationMs
    ) {
    }
}