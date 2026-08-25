package com.leo.careerforgeai.agent.evaluation.concurrency;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRateLimiter;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAdmissionGate;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncDispatcher;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncTask;
import com.leo.careerforgeai.agent.application.run.execution.RunAdmissionLease;
import com.leo.careerforgeai.agent.application.run.lifecycle.CoachingRunLifecycleApplicationService;
import com.leo.careerforgeai.agent.application.run.ratelimit.CoachingRunRateLimitDecision;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunAcceptanceApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunAsyncSubmissionApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimResult;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunRequestFingerprintService;
import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionVersionConflictException;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证并发Acceptance发生Session版本冲突时的幂等返回和失败Run收敛
 * @author: Miao Zheng
 * @date: 2026-08-25
 */
class CoachingRunConcurrentAcceptanceConvergenceTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID SESSION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID USER_TURN_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final String MESSAGE = "请分析我的Java并发能力";

    @Test
    void shouldReturnRunProgressedByConcurrentAcceptanceWithoutDispatchingAgain() {
        TestDependencies dependencies = new TestDependencies();
        CoachingRun received = receivedRun();
        CoachingRun accepted = received.accept(
                USER_TURN_ID,
                NOW.minusSeconds(1)
        );
        RunAdmissionLease lease = dependencies.lease();

        when(dependencies.claimService.claim(
                SESSION_ID,
                REQUEST_ID,
                4,
                MESSAGE
        )).thenReturn(new CoachingRunClaimResult(received, true));
        when(dependencies.dispatcher.acquire(OWNER)).thenReturn(lease);
        when(dependencies.acceptanceService.accept(RUN_ID, MESSAGE))
                .thenThrow(new CoachingSessionVersionConflictException(
                        "Session并发更新冲突"
                ));
        when(dependencies.lifecycleService.rejectReceivedForActor(
                OWNER,
                RUN_ID,
                "SESSION_VERSION_DRIFT"
        )).thenReturn(accepted);

        CoachingRun result = dependencies.service().submit(
                SESSION_ID,
                REQUEST_ID,
                4,
                MESSAGE
        );

        assertThat(result).isSameAs(accepted);
        assertThat(lease.isReleased()).isTrue();
        verify(dependencies.dispatcher, never()).dispatch(any(), any(), any());
        verifyNoInteractions(dependencies.rateLimiter, dependencies.asyncTask);
    }

    @Test
    void shouldRejectDifferentRunThatLostSessionVersionCompetition() {
        TestDependencies dependencies = new TestDependencies();
        CoachingRun received = receivedRun();
        CoachingRun rejected = received.reject(
                "SESSION_VERSION_DRIFT",
                NOW
        );
        RunAdmissionLease lease = dependencies.lease();
        CoachingSessionVersionConflictException conflict =
                new CoachingSessionVersionConflictException(
                        "Session并发更新冲突"
                );

        when(dependencies.claimService.claim(
                SESSION_ID,
                REQUEST_ID,
                4,
                MESSAGE
        )).thenReturn(new CoachingRunClaimResult(received, false));
        when(dependencies.rateLimiter.acquire(OWNER))
                .thenReturn(new CoachingRunRateLimitDecision(
                        true,
                        9,
                        Duration.ofMinutes(1)
                ));
        when(dependencies.dispatcher.acquire(OWNER)).thenReturn(lease);
        when(dependencies.acceptanceService.accept(RUN_ID, MESSAGE))
                .thenThrow(conflict);
        when(dependencies.lifecycleService.rejectReceivedForActor(
                OWNER,
                RUN_ID,
                "SESSION_VERSION_DRIFT"
        )).thenReturn(rejected);

        assertThatThrownBy(() -> dependencies.service().submit(
                SESSION_ID,
                REQUEST_ID,
                4,
                MESSAGE
        )).isSameAs(conflict);

        assertThat(lease.isReleased()).isTrue();
        verify(dependencies.dispatcher, never()).dispatch(any(), any(), any());
        verifyNoInteractions(dependencies.asyncTask);
    }

    private static CoachingRun receivedRun() {
        CoachingRunRequestFingerprintService fingerprintService =
                new CoachingRunRequestFingerprintService();

        return CoachingRun.receive(
                RUN_ID,
                OWNER,
                SESSION_ID,
                REQUEST_ID,
                fingerprintService.fingerprint(
                        SESSION_ID,
                        4,
                        MESSAGE
                ),
                4,
                NOW.minusSeconds(10)
        );
    }

    /**
     * @program: CareerForge-AI
     * @description: 集中创建并发Acceptance收敛测试使用的Mock依赖和准入许可
     * @author: Miao Zheng
     * @date: 2026-08-25
     */
    private static final class TestDependencies {

        private final CurrentActorProvider actorProvider =
                mock(CurrentActorProvider.class);
        private final CoachingRunClaimApplicationService claimService =
                mock(CoachingRunClaimApplicationService.class);
        private final CoachingRunAcceptanceApplicationService acceptanceService =
                mock(CoachingRunAcceptanceApplicationService.class);
        private final CoachingRunLifecycleApplicationService lifecycleService =
                mock(CoachingRunLifecycleApplicationService.class);
        private final CoachingRunRateLimiter rateLimiter =
                mock(CoachingRunRateLimiter.class);
        private final CoachingRunAsyncTask asyncTask =
                mock(CoachingRunAsyncTask.class);
        private final CoachingRunAsyncDispatcher dispatcher =
                mock(CoachingRunAsyncDispatcher.class);
        private final CoachingRunExecutionProperties properties =
                new CoachingRunExecutionProperties(
                        2,
                        1,
                        Duration.ofSeconds(90),
                        Duration.ofSeconds(1)
                );

        private TestDependencies() {
            when(actorProvider.currentActor()).thenReturn(OWNER);
        }

        private CoachingRunAsyncSubmissionApplicationService service() {
            return new CoachingRunAsyncSubmissionApplicationService(
                    actorProvider,
                    claimService,
                    acceptanceService,
                    lifecycleService,
                    rateLimiter,
                    asyncTask,
                    dispatcher,
                    properties,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );
        }

        private RunAdmissionLease lease() {
            return new CoachingRunAdmissionGate(properties)
                    .tryAcquire(OWNER)
                    .orElseThrow();
        }
    }
}