package com.leo.careerforgeai.agent.evaluation.contextcache;

/**
 * @program: CareerForge-AI
 * @description: 汇总一个Context缓存候选的收益、成本与安全边界
 * @author: Miao Zheng
 * @date: 2026-08-24
 * @param candidate 缓存候选类型
 * @param sampleCount 配对测量次数
 * @param cacheHits 缓存命中次数
 * @param cacheHitRate 缓存命中率
 * @param baselineP50Nanos MySQL基线p50耗时
 * @param baselineP95Nanos MySQL基线p95耗时
 * @param candidateP50Nanos Redis候选方案p50耗时
 * @param candidateP95Nanos Redis候选方案p95耗时
 * @param baselineMySqlQueries MySQL基线总查询次数
 * @param candidateMySqlQueries 候选方案总MySQL查询次数
 * @param mySqlQueryReductionRate MySQL查询次数减少比例
 * @param redisCommands Redis命令总数
 * @param maxRedisMemoryBytes 单个候选Key观测到的最大Redis内存
 * @param redisFailureCases Redis故障注入次数
 * @param successfulFallbacks Redis故障后成功回源次数
 * @param ownerValidationComplete 是否所有样本都通过owner校验
 * @param versionValidationComplete 是否所有样本都通过版本校验
 * @param confirmedOnlyComplete 是否所有画像样本都只包含CONFIRMED Memory
 */
public record ContextCacheEvaluationReport(
        ContextCacheCandidate candidate,
        int sampleCount,
        long cacheHits,
        double cacheHitRate,
        long baselineP50Nanos,
        long baselineP95Nanos,
        long candidateP50Nanos,
        long candidateP95Nanos,
        long baselineMySqlQueries,
        long candidateMySqlQueries,
        double mySqlQueryReductionRate,
        long redisCommands,
        long maxRedisMemoryBytes,
        long redisFailureCases,
        long successfulFallbacks,
        boolean ownerValidationComplete,
        boolean versionValidationComplete,
        boolean confirmedOnlyComplete
) {
}