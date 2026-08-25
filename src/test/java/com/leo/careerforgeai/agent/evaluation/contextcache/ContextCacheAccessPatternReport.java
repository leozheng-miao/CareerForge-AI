package com.leo.careerforgeai.agent.evaluation.contextcache;

/**
 * @program: CareerForge-AI
 * @description: 汇总Context候选的版本复用机会和进入真实性能Smoke的资格
 * @author: Miao Zheng
 * @date: 2026-08-24
 * @param candidate 缓存候选类型
 * @param accessCount 总访问次数
 * @param distinctVersionedKeys 不同版本Key数量
 * @param theoreticalHits 相同版本Key再次出现的理论命中次数
 * @param theoreticalHitRate 理论命中率
 * @param versionChanges 同一资源发生的版本切换次数
 * @param versionCoverageComplete 版本是否覆盖全部缓存载荷
 * @param performanceSmokeEligible 是否值得进入真实MySQL与Redis配对测试
 */
public record ContextCacheAccessPatternReport(
        ContextCacheCandidate candidate,
        int accessCount,
        int distinctVersionedKeys,
        long theoreticalHits,
        double theoreticalHitRate,
        long versionChanges,
        boolean versionCoverageComplete,
        boolean performanceSmokeEligible
) {
}