package com.leo.careerforgeai.agent.evaluation.failure;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRateLimiter;
import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncDispatcher;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncTask;
import com.leo.careerforgeai.agent.application.run.lifecycle.CoachingRunLifecycleApplicationService;
import com.leo.careerforgeai.agent.application.run.lifecycle.CoachingRunVersionConflictException;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunAcceptanceApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunAsyncSubmissionApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunRequestFingerprintService;
import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionApplicationService;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证MySQL在Run认领、USER Turn和终态CAS阶段故障时的副作用边界
 * @author: Miao Zheng
 * @date: 2026-08-25
 */
class CoachingRunPersistenceFailureMatrixTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID SESSION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID USER_TURN_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ASSISTANT_TURN_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID EXCHANGE_ID =
            UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-25T11:00:00Z");
    private static final String MESSAGE = "请分析我的Java并发能力";

    @Test
    void shouldStopAllDownstreamWorkWhenRunClaimFails() {
        CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
        CoachingRunClaimApplicationService claimService =
                mock(CoachingRunClaimApplicationService.class);
        CoachingRunAcceptanceApplicationService acceptanceService =
                mock(CoachingRunAcceptanceApplicationService.class);
        CoachingRunLifecycleApplicationService lifecycleService =
                mock(CoachingRunLifecycleApplicationService.class);
        CoachingRunRateLimiter rateLimiter =
                mock(CoachingRunRateLimiter.class);
        CoachingRunAsyncTask asyncTask =
                mock(CoachingRunAsyncTask.class);
        CoachingRunAsyncDispatcher dispatcher =
                mock(CoachingRunAsyncDispatcher.class);

        when(actorProvider.currentActor()).thenReturn(OWNER);
        when(claimService.claim(
                SESSION_ID,
                REQUEST_ID,
                4,
                MESSAGE
        )).thenThrow(new IllegalStateException("MySQL Run认领失败"));

        CoachingRunAsyncSubmissionApplicationService service =
                new CoachingRunAsyncSubmissionApplicationService(
                        actorProvider,
                        claimService,
                        acceptanceService,
                        lifecycleService,
                        rateLimiter,
                        asyncTask,
                        dispatcher,
                        executionProperties(),
                        fixedClock()
                );

        assertThatThrownBy(() -> service.submit(
                SESSION_ID,
                REQUEST_ID,
                4,
                MESSAGE
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("MySQL Run认领失败");

        verifyNoInteractions(
                acceptanceService,
                lifecycleService,
                rateLimiter,
                asyncTask,
                dispatcher
        );
    }

    @Test
    void shouldKeepRunUnacceptedWhenUserTurnPersistenceFails() {
        CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
        CoachingRunRepository repository =
                mock(CoachingRunRepository.class);
        CoachingSessionApplicationService sessionService =
                mock(CoachingSessionApplicationService.class);
        CoachingRunRequestFingerprintService fingerprintService =
                new CoachingRunRequestFingerprintService();
        CoachingRun received = receivedRun(fingerprintService);

        when(actorProvider.currentActor()).thenReturn(OWNER);
        when(repository.findByRunId(OWNER, RUN_ID))
                .thenReturn(Optional.of(received));
        when(sessionService.recordUserTurn(
                SESSION_ID,
                4,
                MESSAGE
        )).thenThrow(new IllegalStateException("MySQL USER Turn写入失败"));

        CoachingRunAcceptanceApplicationService service =
                new CoachingRunAcceptanceApplicationService(
                        actorProvider,
                        repository,
                        fingerprintService,
                        sessionService,
                        fixedClock()
                );

        assertThatThrownBy(() -> service.accept(RUN_ID, MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MySQL USER Turn写入失败");

        verify(repository, never()).updateIfVersionMatches(
                any(),
                any(),
                anyLong()
        );
    }

    @Test
    void shouldExposeTerminalCasConflictInsteadOfReturningFalseSuccess() {
        CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
        CoachingRunRepository repository =
                mock(CoachingRunRepository.class);
        CoachingSessionApplicationService sessionService =
                mock(CoachingSessionApplicationService.class);
        CoachingRun running = runningRun();
        ConversationTurn assistantTurn = completedAssistantTurn();

        when(repository.findByRunId(OWNER, RUN_ID))
                .thenReturn(Optional.of(running));
        when(sessionService.recordValidatedAssistantTurnForActor(
                OWNER,
                SESSION_ID,
                5,
                USER_TURN_ID,
                "可信回答",
                "agent-run-success"
        )).thenReturn(assistantTurn);
        when(repository.updateIfVersionMatches(
                eq(OWNER),
                any(CoachingRun.class),
                eq(2L)
        )).thenReturn(false);

        CoachingRunLifecycleApplicationService service =
                new CoachingRunLifecycleApplicationService(
                        actorProvider,
                        repository,
                        sessionService,
                        fixedClock()
                );

        assertThatThrownBy(() -> service.succeedForActor(
                OWNER,
                RUN_ID,
                "可信回答",
                "agent-run-success"
        )).isInstanceOfSatisfying(
                CoachingRunVersionConflictException.class,
                exception -> {
                    assertThat(exception.runId()).isEqualTo(RUN_ID);
                    assertThat(exception.expectedVersion()).isEqualTo(2);
                }
        );

        verify(repository).updateIfVersionMatches(
                eq(OWNER),
                any(CoachingRun.class),
                eq(2L)
        );
    }

    private CoachingRun receivedRun(
            CoachingRunRequestFingerprintService fingerprintService
    ) {
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
                NOW.minusSeconds(30)
        );
    }

    private CoachingRun runningRun() {
        return CoachingRun.receive(
                RUN_ID,
                OWNER,
                SESSION_ID,
                REQUEST_ID,
                "a".repeat(64),
                4,
                NOW.minusSeconds(30)
        ).accept(
                USER_TURN_ID,
                NOW.minusSeconds(20)
        ).start(
                NOW.minusSeconds(10)
        );
    }

    private ConversationTurn completedAssistantTurn() {
        return ConversationTurn.completedAssistant(
                ASSISTANT_TURN_ID,
                SESSION_ID,
                EXCHANGE_ID,
                OWNER,
                6,
                "可信回答",
                "agent-run-success",
                NOW
        );
    }

    private CoachingRunExecutionProperties executionProperties() {
        return new CoachingRunExecutionProperties(
                2,
                1,
                Duration.ofSeconds(90),
                Duration.ofSeconds(1)
        );
    }

    private Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}