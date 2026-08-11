package com.leo.careerforgeai.agent.evaluation.metrics;

import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 聚合同一执行模式下的Agent单Case指标，保留每项指标的真实分子和分母。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
public final class AgentMetricsAggregator {

    public AgentEvaluationSummary aggregate(
            List<AgentCaseMetricsCalculator.AgentCaseMetrics> measurements
    ) {
        if (measurements == null || measurements.isEmpty()) {
            throw new IllegalArgumentException("measurements不能为空");
        }
        if (measurements.stream().anyMatch(measurement -> measurement == null)) {
            throw new IllegalArgumentException("measurements不能包含空元素");
        }

        AgentCaseMeasurement.ExecutionMode executionMode = measurements.getFirst().executionMode();
        Set<String> caseRuns = new HashSet<>();
        Set<String> distinctCases = new HashSet<>();

        for (AgentCaseMetricsCalculator.AgentCaseMetrics measurement : measurements) {
            if (measurement.executionMode() != executionMode) {
                throw new IllegalArgumentException("同一次聚合不能混合STUB和REAL结果");
            }
            String caseRun = measurement.caseId() + "#" + measurement.runNumber();
            if (!caseRuns.add(caseRun)) throw new IllegalArgumentException("同一个Case运行编号不能重复");
            distinctCases.add(measurement.caseId());
        }

        long requiredToolHits = measurements.stream()
                .mapToLong(AgentCaseMetricsCalculator.AgentCaseMetrics::requiredToolHits)
                .sum();
        long requiredToolCount = measurements.stream()
                .mapToLong(AgentCaseMetricsCalculator.AgentCaseMetrics::requiredToolCount)
                .sum();
        long unnecessaryToolCalls = measurements.stream()
                .mapToLong(AgentCaseMetricsCalculator.AgentCaseMetrics::unnecessaryToolCalls)
                .sum();
        long requestedToolCalls = measurements.stream()
                .mapToLong(AgentCaseMetricsCalculator.AgentCaseMetrics::requestedToolCalls)
                .sum();
        long validArgumentCalls = measurements.stream()
                .mapToLong(AgentCaseMetricsCalculator.AgentCaseMetrics::validArgumentCalls)
                .sum();
        long sequenceApplicable = measurements.stream()
                .filter(AgentCaseMetricsCalculator.AgentCaseMetrics::sequenceApplicable)
                .count();
        long sequenceCorrect = measurements.stream()
                .filter(AgentCaseMetricsCalculator.AgentCaseMetrics::sequenceApplicable)
                .filter(AgentCaseMetricsCalculator.AgentCaseMetrics::sequenceCorrect)
                .count();
        long taskSucceeded = measurements.stream()
                .filter(AgentCaseMetricsCalculator.AgentCaseMetrics::taskSucceeded)
                .count();
        long citationApplicable = measurements.stream()
                .filter(AgentCaseMetricsCalculator.AgentCaseMetrics::citationApplicable)
                .count();
        long citationLegal = measurements.stream()
                .filter(AgentCaseMetricsCalculator.AgentCaseMetrics::citationApplicable)
                .filter(AgentCaseMetricsCalculator.AgentCaseMetrics::citationLegal)
                .count();
        long loopTerminated = measurements.stream()
                .filter(AgentCaseMetricsCalculator.AgentCaseMetrics::loopTerminatedAsExpected)
                .count();
        long failureRecoveryApplicable = measurements.stream()
                .filter(AgentCaseMetricsCalculator.AgentCaseMetrics::toolFailureRecoveryApplicable)
                .count();
        long failureRecovered = measurements.stream()
                .filter(AgentCaseMetricsCalculator.AgentCaseMetrics::toolFailureRecoveryApplicable)
                .filter(AgentCaseMetricsCalculator.AgentCaseMetrics::toolFailureRecovered)
                .count();

        int caseRunCount = measurements.size();
        long totalOuterModelTokens = measurements.stream()
                .mapToLong(AgentCaseMetricsCalculator.AgentCaseMetrics::outerModelTokens)
                .sum();
        long totalToolModelTokens = measurements.stream()
                .mapToLong(AgentCaseMetricsCalculator.AgentCaseMetrics::toolModelTokens)
                .sum();
        List<Long> sortedDurations = measurements.stream()
                .map(AgentCaseMetricsCalculator.AgentCaseMetrics::durationMs)
                .sorted()
                .toList();

        return new AgentEvaluationSummary(
                executionMode,
                distinctCases.size(),
                caseRunCount,
                new MetricRatio(requiredToolHits, requiredToolCount),
                new MetricRatio(unnecessaryToolCalls, requestedToolCalls),
                new MetricRatio(validArgumentCalls, requestedToolCalls),
                new MetricRatio(sequenceCorrect, sequenceApplicable),
                new MetricRatio(taskSucceeded, caseRunCount),
                new MetricRatio(citationLegal, citationApplicable),
                new MetricRatio(loopTerminated, caseRunCount),
                new MetricRatio(failureRecovered, failureRecoveryApplicable),
                measurements.stream()
                        .mapToInt(AgentCaseMetricsCalculator.AgentCaseMetrics::requestedToolCalls)
                        .average()
                        .orElseThrow(),
                measurements.stream()
                        .mapToInt(AgentCaseMetricsCalculator.AgentCaseMetrics::modelIterations)
                        .average()
                        .orElseThrow(),
                totalOuterModelTokens,
                totalToolModelTokens,
                totalOuterModelTokens / (double) caseRunCount,
                totalToolModelTokens / (double) caseRunCount,
                nearestRankPercentile(sortedDurations, 0.50),
                nearestRankPercentile(sortedDurations, 0.95)
        );
    }

    private long nearestRankPercentile(List<Long> sortedValues, double percentile) {
        int rank = (int) Math.ceil(percentile * sortedValues.size());
        return sortedValues.get(Math.max(rank, 1) - 1);
    }

    public record AgentEvaluationSummary(
            AgentCaseMeasurement.ExecutionMode executionMode,
            int distinctCaseCount,
            int caseRunCount,
            MetricRatio requiredToolRecall,
            MetricRatio unnecessaryToolCallRate,
            MetricRatio argumentValidRate,
            MetricRatio toolSequenceAccuracy,
            MetricRatio taskSuccessRate,
            MetricRatio citationLegalRate,
            MetricRatio loopTerminationRate,
            MetricRatio toolFailureRecoveryRate,
            double averageRequestedToolCalls,
            double averageModelIterations,
            long totalOuterModelTokens,
            long totalToolModelTokens,
            double averageOuterModelTokens,
            double averageToolModelTokens,
            long p50DurationMs,
            long p95DurationMs
    ) {
    }

    public record MetricRatio(long numerator, long denominator) {

        public MetricRatio {
            if (numerator < 0 || denominator < 0) throw new IllegalArgumentException("指标分子和分母不能小于0");
            if (numerator > denominator) throw new IllegalArgumentException("指标分子不能大于分母");
        }

        public OptionalDouble value() {
            return denominator == 0
                    ? OptionalDouble.empty()
                    : OptionalDouble.of(numerator / (double) denominator);
        }
    }
}