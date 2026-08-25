package com.leo.careerforgeai.agent.evaluation.contextcache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 聚合Context缓存配对测量并保留命中率、分位数、查询成本和安全证据
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
public final class ContextCacheEvaluationAggregator {

    public ContextCacheEvaluationReport aggregate(
            List<ContextCacheMeasurement> measurements
    ) {
        if (measurements == null || measurements.isEmpty()) {
            throw new IllegalArgumentException("measurements不能为空");
        }
        if (measurements.stream().anyMatch(measurement -> measurement == null)) {
            throw new IllegalArgumentException("measurements不能包含null");
        }

        ContextCacheCandidate candidate = measurements.getFirst().candidate();
        Set<Integer> runNumbers = new HashSet<>();

        for (ContextCacheMeasurement measurement : measurements) {
            if (measurement.candidate() != candidate) {
                throw new IllegalArgumentException("同一次聚合不能混合不同缓存候选");
            }
            if (!runNumbers.add(measurement.runNumber())) {
                throw new IllegalArgumentException("runNumber不能重复");
            }
        }

        List<Long> baselineDurations = measurements.stream()
                .map(ContextCacheMeasurement::baselineDurationNanos)
                .sorted()
                .toList();
        List<Long> candidateDurations = measurements.stream()
                .map(ContextCacheMeasurement::candidateDurationNanos)
                .sorted()
                .toList();

        long cacheHits = measurements.stream()
                .filter(ContextCacheMeasurement::cacheHit)
                .count();
        long baselineQueries = measurements.stream()
                .mapToLong(ContextCacheMeasurement::baselineMySqlQueries)
                .sum();
        long candidateQueries = measurements.stream()
                .mapToLong(ContextCacheMeasurement::candidateMySqlQueries)
                .sum();
        long redisFailureCases = measurements.stream()
                .filter(ContextCacheMeasurement::redisFailureInjected)
                .count();
        long successfulFallbacks = measurements.stream()
                .filter(ContextCacheMeasurement::redisFailureInjected)
                .filter(ContextCacheMeasurement::fallbackSucceeded)
                .count();

        return new ContextCacheEvaluationReport(
                candidate,
                measurements.size(),
                cacheHits,
                cacheHits / (double) measurements.size(),
                nearestRankPercentile(baselineDurations, 0.50),
                nearestRankPercentile(baselineDurations, 0.95),
                nearestRankPercentile(candidateDurations, 0.50),
                nearestRankPercentile(candidateDurations, 0.95),
                baselineQueries,
                candidateQueries,
                (baselineQueries - candidateQueries) / (double) baselineQueries,
                measurements.stream()
                        .mapToLong(ContextCacheMeasurement::redisCommands)
                        .sum(),
                measurements.stream()
                        .mapToLong(ContextCacheMeasurement::redisMemoryBytes)
                        .max()
                        .orElse(0),
                redisFailureCases,
                successfulFallbacks,
                measurements.stream().allMatch(ContextCacheMeasurement::ownerValidated),
                measurements.stream().allMatch(ContextCacheMeasurement::versionValidated),
                measurements.stream().allMatch(ContextCacheMeasurement::confirmedOnly)
        );
    }

    private long nearestRankPercentile(
            List<Long> sortedValues,
            double percentile
    ) {
        int rank = (int) Math.ceil(percentile * sortedValues.size());
        return sortedValues.get(Math.max(rank, 1) - 1);
    }
}