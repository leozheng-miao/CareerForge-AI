package com.leo.careerforgeai.agent.evaluation.contextcache;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证Session版本高频变化和画像版本覆盖不完整时不会错误进入缓存性能测试
 * @author: Miao Zheng
 * @date: 2026-08-24
 */
class ContextCacheAccessPatternEvaluatorTest {

    private static final String OWNER_KEY = "owner-sha256";
    private static final String SESSION_KEY = "session-1";

    @Test
    void shouldRejectSessionCandidateWithoutVersionReuse() {
        List<ContextCacheVersionedAccess> accesses = List.of(
                sessionAccess(1, 1),
                sessionAccess(2, 3),
                sessionAccess(3, 5),
                sessionAccess(4, 7),
                sessionAccess(5, 9)
        );

        ContextCacheAccessPatternEvaluator evaluator =
                new ContextCacheAccessPatternEvaluator();

        ContextCacheAccessPatternReport report =
                evaluator.evaluate(accesses);

        assertThat(report.candidate()).isEqualTo(ContextCacheCandidate.SESSION);
        assertThat(report.accessCount()).isEqualTo(5);
        assertThat(report.distinctVersionedKeys()).isEqualTo(5);
        assertThat(report.theoreticalHits()).isZero();
        assertThat(report.theoreticalHitRate()).isZero();
        assertThat(report.versionChanges()).isEqualTo(4);
        assertThat(report.versionCoverageComplete()).isTrue();
        assertThat(report.performanceSmokeEligible()).isFalse();
        assertThat(evaluator.decide(report))
                .isEqualTo(ContextCacheGateDecision.REJECT_NO_VERSION_REUSE);
    }

    @Test
    void shouldRejectProfileCandidateWhenVersionDoesNotCoverAllConfirmedMemory() {
        List<ContextCacheVersionedAccess> accesses = List.of(
                profileAccess(1),
                profileAccess(2),
                profileAccess(3),
                profileAccess(4),
                profileAccess(5)
        );

        ContextCacheAccessPatternEvaluator evaluator =
                new ContextCacheAccessPatternEvaluator();

        ContextCacheAccessPatternReport report =
                evaluator.evaluate(accesses);

        assertThat(report.candidate())
                .isEqualTo(ContextCacheCandidate.CONFIRMED_PROFILE);
        assertThat(report.accessCount()).isEqualTo(5);
        assertThat(report.distinctVersionedKeys()).isEqualTo(1);
        assertThat(report.theoreticalHits()).isEqualTo(4);
        assertThat(report.theoreticalHitRate()).isEqualTo(0.8);
        assertThat(report.versionChanges()).isZero();
        assertThat(report.versionCoverageComplete()).isFalse();
        assertThat(report.performanceSmokeEligible()).isFalse();
        assertThat(evaluator.decide(report))
                .isEqualTo(
                        ContextCacheGateDecision.REJECT_INCOMPLETE_VERSION_COVERAGE
                );
    }

    @Test
    void shouldRequireRealSmokeAfterVersionAndReusePreconditionsPass() {
        List<ContextCacheVersionedAccess> accesses = List.of(
                new ContextCacheVersionedAccess(
                        ContextCacheCandidate.CONFIRMED_PROFILE,
                        1,
                        OWNER_KEY,
                        "confirmed-memory-profile",
                        7,
                        true
                ),
                new ContextCacheVersionedAccess(
                        ContextCacheCandidate.CONFIRMED_PROFILE,
                        2,
                        OWNER_KEY,
                        "confirmed-memory-profile",
                        7,
                        true
                )
        );
        ContextCacheAccessPatternEvaluator evaluator =
                new ContextCacheAccessPatternEvaluator();

        ContextCacheAccessPatternReport report =
                evaluator.evaluate(accesses);

        assertThat(report.theoreticalHits()).isEqualTo(1);
        assertThat(report.versionCoverageComplete()).isTrue();
        assertThat(evaluator.decide(report))
                .isEqualTo(
                        ContextCacheGateDecision.REQUIRE_REAL_MYSQL_REDIS_SMOKE
                );
    }

    private ContextCacheVersionedAccess sessionAccess(
            int sequence,
            long sessionVersion
    ) {
        return new ContextCacheVersionedAccess(
                ContextCacheCandidate.SESSION,
                sequence,
                OWNER_KEY,
                SESSION_KEY,
                sessionVersion,
                true
        );
    }

    private ContextCacheVersionedAccess profileAccess(int sequence) {
        return new ContextCacheVersionedAccess(
                ContextCacheCandidate.CONFIRMED_PROFILE,
                sequence,
                OWNER_KEY,
                "confirmed-memory-profile",
                7,
                false
        );
    }
}