package com.leo.careerforgeai.agent.evaluation.contextcache;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 根据版本化访问轨迹计算理论缓存命中率并执行版本安全前置门禁
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
public final class ContextCacheAccessPatternEvaluator {

    public ContextCacheAccessPatternReport evaluate(
            List<ContextCacheVersionedAccess> accesses
    ) {
        if (accesses == null || accesses.isEmpty()) {
            throw new IllegalArgumentException("accesses不能为空");
        }
        if (accesses.stream().anyMatch(access -> access == null)) {
            throw new IllegalArgumentException("accesses不能包含null");
        }

        ContextCacheCandidate candidate = accesses.getFirst().candidate();
        Set<String> versionedKeys = new HashSet<>();
        Map<String, Long> lastVersionByResource = new HashMap<>();
        long theoreticalHits = 0;
        long versionChanges = 0;

        for (int index = 0; index < accesses.size(); index++) {
            ContextCacheVersionedAccess access = accesses.get(index);
            if (access.sequence() != index + 1) {
                throw new IllegalArgumentException("sequence必须从1连续递增");
            }
            if (access.candidate() != candidate) {
                throw new IllegalArgumentException("同一次评估不能混合不同缓存候选");
            }
            if (!versionedKeys.add(access.versionedIdentity())) theoreticalHits++;

            Long previousVersion = lastVersionByResource.put(
                    access.resourceIdentity(),
                    access.version()
            );
            if (previousVersion != null && previousVersion != access.version()) {
                versionChanges++;
            }
        }

        boolean versionCoverageComplete = accesses.stream()
                .allMatch(ContextCacheVersionedAccess::versionCoversPayload);
        boolean performanceSmokeEligible =
                versionCoverageComplete && theoreticalHits > 0;

        return new ContextCacheAccessPatternReport(
                candidate,
                accesses.size(),
                versionedKeys.size(),
                theoreticalHits,
                theoreticalHits / (double) accesses.size(),
                versionChanges,
                versionCoverageComplete,
                performanceSmokeEligible
        );
    }

    public ContextCacheGateDecision decide(
            ContextCacheAccessPatternReport report
    ) {
        if (report == null) throw new IllegalArgumentException("report不能为空");
        if (!report.versionCoverageComplete()) {
            return ContextCacheGateDecision.REJECT_INCOMPLETE_VERSION_COVERAGE;
        }
        if (report.theoreticalHits() == 0) {
            return ContextCacheGateDecision.REJECT_NO_VERSION_REUSE;
        }
        return ContextCacheGateDecision.REQUIRE_REAL_MYSQL_REDIS_SMOKE;
    }
}