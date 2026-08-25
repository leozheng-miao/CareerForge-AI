
package com.leo.careerforgeai.agent.evaluation.contextcache;

/**
 * @program: CareerForge-AI
 * @description: 保存同一输入下MySQL基线与Redis候选方案的一次配对测量事实
 * @author: Miao Zheng
 * @date: 2026-08-24
 * @param candidate 缓存候选类型
 * @param runNumber 同一候选下从1开始的运行编号
 * @param cacheHit 本次候选读取是否命中缓存
 * @param baselineDurationNanos MySQL基线读取耗时
 * @param candidateDurationNanos Redis候选方案端到端读取耗时
 * @param baselineMySqlQueries MySQL基线查询次数
 * @param candidateMySqlQueries 候选方案包含版本复核和回源在内的MySQL查询次数
 * @param redisCommands 候选方案执行的Redis命令数
 * @param redisMemoryBytes 本次缓存Key通过MEMORY USAGE观测的字节数
 * @param ownerValidated 候选结果是否完成owner边界校验
 * @param versionValidated 候选结果是否完成Session或画像版本校验
 * @param confirmedOnly 候选画像是否只包含CONFIRMED Memory
 * @param redisFailureInjected 本次是否注入Redis不可用
 * @param fallbackSucceeded Redis不可用时是否成功回退MySQL并返回完整结果
 */
public record ContextCacheMeasurement(
        ContextCacheCandidate candidate,
        int runNumber,
        boolean cacheHit,
        long baselineDurationNanos,
        long candidateDurationNanos,
        int baselineMySqlQueries,
        int candidateMySqlQueries,
        int redisCommands,
        long redisMemoryBytes,
        boolean ownerValidated,
        boolean versionValidated,
        boolean confirmedOnly,
        boolean redisFailureInjected,
        boolean fallbackSucceeded
) {

    public ContextCacheMeasurement {
        if (candidate == null) throw new IllegalArgumentException("candidate不能为空");
        if (runNumber <= 0) throw new IllegalArgumentException("runNumber必须大于0");
        if (baselineDurationNanos < 0 || candidateDurationNanos < 0) {
            throw new IllegalArgumentException("读取耗时不能小于0");
        }
        if (baselineMySqlQueries <= 0) {
            throw new IllegalArgumentException("baselineMySqlQueries必须大于0");
        }
        if (candidateMySqlQueries < 0 || redisCommands < 0 || redisMemoryBytes < 0) {
            throw new IllegalArgumentException("查询数、命令数和Redis内存不能小于0");
        }
        if (cacheHit && redisCommands == 0) {
            throw new IllegalArgumentException("缓存命中时redisCommands必须大于0");
        }
        if (redisFailureInjected && cacheHit) {
            throw new IllegalArgumentException("Redis故障场景不能记录为缓存命中");
        }
        if (!redisFailureInjected && fallbackSucceeded) {
            throw new IllegalArgumentException("非Redis故障场景不能记录fallbackSucceeded");
        }
    }
}