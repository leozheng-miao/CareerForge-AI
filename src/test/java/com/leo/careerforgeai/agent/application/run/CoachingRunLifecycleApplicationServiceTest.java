package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionApplicationService;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证Run启动、成功、失败、超时、终态重放和CAS冲突
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@ExtendWith(MockitoExtension.class)
class CoachingRunLifecycleApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID USER_TURN_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ASSISTANT_TURN_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID EXCHANGE_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-20T04:00:00Z");
    private static final String FINGERPRINT = "a".repeat(64);

    @Mock
    private CurrentActorProvider currentActorProvider;

    @Mock
    private CoachingRunRepository repository;

    @Mock
    private CoachingSessionApplicationService sessionApplicationService;

    private CoachingRunLifecycleApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CoachingRunLifecycleApplicationService(
                currentActorProvider,
                repository,
                sessionApplicationService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(currentActorProvider.currentActor()).thenReturn(OWNER);
    }

    @Test
    void shouldStartAcceptedRunWithCas() {
        CoachingRun accepted = acceptedRun();
        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(Optional.of(accepted));
        when(repository.updateIfVersionMatches(eq(OWNER), any(CoachingRun.class), eq(1L)))
                .thenReturn(true);

        CoachingRunStartResult result = service.start(RUN_ID);

        assertThat(result.started()).isTrue();
        assertThat(result.run().status()).isEqualTo(CoachingRunStatus.RUNNING);
        assertThat(result.run().version()).isEqualTo(2L);
        assertThat(result.run().startedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldReplayRunningRunWithoutAnotherCas() {
        CoachingRun running = runningRun();
        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(Optional.of(running));

        CoachingRunStartResult result = service.start(RUN_ID);

        assertThat(result.started()).isFalse();
        assertThat(result.run()).isSameAs(running);
        verify(repository, never()).updateIfVersionMatches(any(), any(), org.mockito.ArgumentMatchers.anyLong());    }

    @Test
    void shouldSaveValidatedAssistantAndSucceedRun() {
        CoachingRun running = runningRun();
        ConversationTurn assistantTurn = completedAssistantTurn();

        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(Optional.of(running));
        when(sessionApplicationService.recordValidatedAssistantTurn(
                SESSION_ID,
                5L,
                USER_TURN_ID,
                "可信回答",
                "agent-run-success"
        )).thenReturn(assistantTurn);
        when(repository.updateIfVersionMatches(eq(OWNER), any(CoachingRun.class), eq(2L)))
                .thenReturn(true);

        CoachingRun succeeded = service.succeed(
                RUN_ID,
                "可信回答",
                "agent-run-success"
        );

        assertThat(succeeded.status()).isEqualTo(CoachingRunStatus.SUCCEEDED);
        assertThat(succeeded.assistantTurnId()).isEqualTo(ASSISTANT_TURN_ID);
        assertThat(succeeded.version()).isEqualTo(3L);
        assertThat(succeeded.finishedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldSaveFailedAssistantAndFailRun() {
        CoachingRun running = runningRun();

        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(Optional.of(running));
        when(sessionApplicationService.recordFailedAssistantTurn(
                SESSION_ID,
                5L,
                USER_TURN_ID,
                "agent-run-failed",
                "MODEL_ERROR"
        )).thenReturn(failedAssistantTurn("agent-run-failed", "MODEL_ERROR"));
        when(repository.updateIfVersionMatches(eq(OWNER), any(CoachingRun.class), eq(2L)))
                .thenReturn(true);

        CoachingRun failed = service.fail(
                RUN_ID,
                "agent-run-failed",
                "MODEL_ERROR"
        );

        assertThat(failed.status()).isEqualTo(CoachingRunStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("MODEL_ERROR");
        assertThat(failed.assistantTurnId()).isEqualTo(ASSISTANT_TURN_ID);
    }

    @Test
    void shouldSaveFailedAssistantAndTimeOutRun() {
        CoachingRun running = runningRun();

        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(Optional.of(running));
        when(sessionApplicationService.recordFailedAssistantTurn(
                SESSION_ID,
                5L,
                USER_TURN_ID,
                "agent-run-timeout",
                "MODEL_TIMEOUT"
        )).thenReturn(failedAssistantTurn("agent-run-timeout", "MODEL_TIMEOUT"));
        when(repository.updateIfVersionMatches(eq(OWNER), any(CoachingRun.class), eq(2L)))
                .thenReturn(true);

        CoachingRun timedOut = service.timeOut(
                RUN_ID,
                "agent-run-timeout",
                "MODEL_TIMEOUT"
        );

        assertThat(timedOut.status()).isEqualTo(CoachingRunStatus.TIMED_OUT);
        assertThat(timedOut.failureCode()).isEqualTo("MODEL_TIMEOUT");
    }

    @Test
    void shouldThrowVersionConflictWhenStartCasFails() {
        CoachingRun accepted = acceptedRun();
        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(Optional.of(accepted));
        when(repository.updateIfVersionMatches(eq(OWNER), any(CoachingRun.class), eq(1L)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.start(RUN_ID))
                .isInstanceOf(CoachingRunVersionConflictException.class)
                .hasMessage("Run版本已经发生变化");
    }

    private CoachingRun acceptedRun() {
        return CoachingRun.receive(
                RUN_ID,
                OWNER,
                SESSION_ID,
                REQUEST_ID,
                FINGERPRINT,
                4L,
                NOW.minusSeconds(30)
        ).accept(USER_TURN_ID, NOW.minusSeconds(20));
    }

    private CoachingRun runningRun() {
        return acceptedRun().start(NOW.minusSeconds(10));
    }

    private ConversationTurn completedAssistantTurn() {
        return ConversationTurn.completedAssistant(
                ASSISTANT_TURN_ID,
                SESSION_ID,
                EXCHANGE_ID,
                OWNER,
                6L,
                "可信回答",
                "agent-run-success",
                NOW
        );
    }

    private ConversationTurn failedAssistantTurn(String agentRunId, String failureCode) {
        return ConversationTurn.failedAssistant(
                ASSISTANT_TURN_ID,
                SESSION_ID,
                EXCHANGE_ID,
                OWNER,
                6L,
                agentRunId,
                failureCode,
                NOW
        );
    }
}