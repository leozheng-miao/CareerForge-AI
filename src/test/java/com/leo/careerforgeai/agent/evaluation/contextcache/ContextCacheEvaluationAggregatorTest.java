
package com.leo.careerforgeai.agent.evaluation.contextcache;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证Context缓存收益报告的命中率、分位数、查询减少和故障回退计算
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
class ContextCacheEvaluationAggregatorTest {

    @Test
    void shouldAggregatePairedContextCacheMeasurements() {
        List<ContextCacheMeasurement> measurements = List.of(
                measurement(1, true, 100, 30, 2, 1, 512, false, false),
                measurement(2, true, 110, 35, 2, 1, 512, false, false),
                measurement(3, true, 120, 40, 2, 1, 512, false, false),
                measurement(4, false, 130, 125, 2, 2, 0, true, true)
        );

        ContextCacheEvaluationReport report =
                new ContextCacheEvaluationAggregator().aggregate(measurements);

        assertThat(report.candidate())
                .isEqualTo(ContextCacheCandidate.SESSION);
        assertThat(report.sampleCount()).isEqualTo(4);
        assertThat(report.cacheHits()).isEqualTo(3);
        assertThat(report.cacheHitRate()).isEqualTo(0.75);
        assertThat(report.baselineP50Nanos()).isEqualTo(110);
        assertThat(report.baselineP95Nanos()).isEqualTo(130);
        assertThat(report.candidateP50Nanos()).isEqualTo(35);
        assertThat(report.candidateP95Nanos()).isEqualTo(125);
        assertThat(report.baselineMySqlQueries()).isEqualTo(8);
        assertThat(report.candidateMySqlQueries()).isEqualTo(5);
        assertThat(report.mySqlQueryReductionRate()).isEqualTo(0.375);
        assertThat(report.redisCommands()).isEqualTo(4);
        assertThat(report.maxRedisMemoryBytes()).isEqualTo(512);
        assertThat(report.redisFailureCases()).isEqualTo(1);
        assertThat(report.successfulFallbacks()).isEqualTo(1);
        assertThat(report.ownerValidationComplete()).isTrue();
        assertThat(report.versionValidationComplete()).isTrue();
        assertThat(report.confirmedOnlyComplete()).isTrue();
    }

    private ContextCacheMeasurement measurement(
            int runNumber,
            boolean cacheHit,
            long baselineDurationNanos,
            long candidateDurationNanos,
            int baselineMySqlQueries,
            int candidateMySqlQueries,
            long redisMemoryBytes,
            boolean redisFailureInjected,
            boolean fallbackSucceeded
    ) {
        return new ContextCacheMeasurement(
                ContextCacheCandidate.SESSION,
                runNumber,
                cacheHit,
                baselineDurationNanos,
                candidateDurationNanos,
                baselineMySqlQueries,
                candidateMySqlQueries,
                1,
                redisMemoryBytes,
                true,
                true,
                true,
                redisFailureInjected,
                fallbackSucceeded
        );
    }
}