package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
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
 * @description: 验证非终态Run中断、终态幂等和CAS冲突
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@ExtendWith(MockitoExtension.class)
class CoachingRunInterruptionApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID USER_TURN_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-08-20T11:50:00Z");
    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    @Mock
    private CoachingRunRepository repository;

    private CoachingRunInterruptionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CoachingRunInterruptionApplicationService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldInterruptAcceptedRunWithoutAssistantTurn() {
        CoachingRun accepted = acceptedRun();

        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(Optional.of(accepted));
        when(repository.updateIfVersionMatches(eq(OWNER), any(CoachingRun.class), eq(1L)))
                .thenReturn(true);

        CoachingRun result = service.interruptForActor(
                OWNER,
                RUN_ID,
                "RUN_EXECUTION_INTERRUPTED"
        );

        assertThat(result.status()).isEqualTo(CoachingRunStatus.INTERRUPTED);
        assertThat(result.failureCode()).isEqualTo("RUN_EXECUTION_INTERRUPTED");
        assertThat(result.userTurnId()).isEqualTo(USER_TURN_ID);
        assertThat(result.assistantTurnId()).isNull();
        assertThat(result.startedAt()).isNull();
        assertThat(result.finishedAt()).isEqualTo(NOW);
        assertThat(result.version()).isEqualTo(2L);
    }

    @Test
    void shouldInterruptRunningRunWithoutAssistantTurn() {
        CoachingRun running = runningRun();

        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(Optional.of(running));
        when(repository.updateIfVersionMatches(eq(OWNER), any(CoachingRun.class), eq(2L)))
                .thenReturn(true);

        CoachingRun result = service.interruptForActor(
                OWNER,
                RUN_ID,
                "RUN_DEADLINE_EXCEEDED"
        );

        assertThat(result.status()).isEqualTo(CoachingRunStatus.INTERRUPTED);
        assertThat(result.failureCode()).isEqualTo("RUN_DEADLINE_EXCEEDED");
        assertThat(result.startedAt()).isEqualTo(CREATED_AT.plusSeconds(20));
        assertThat(result.finishedAt()).isEqualTo(NOW);
        assertThat(result.version()).isEqualTo(3L);
    }

    @Test
    void shouldReturnExistingTerminalRunWithoutAnotherUpdate() {
        CoachingRun interrupted = runningRun().interrupt(
                "RUN_EXECUTION_INTERRUPTED",
                CREATED_AT.plusSeconds(30)
        );

        when(repository.findByRunId(OWNER, RUN_ID))
                .thenReturn(Optional.of(interrupted));

        CoachingRun result = service.interruptForActor(
                OWNER,
                RUN_ID,
                "RUN_DEADLINE_EXCEEDED"
        );

        assertThat(result).isSameAs(interrupted);
        verify(repository, never()).updateIfVersionMatches(any(), any(), any(Long.class));
    }

    @Test
    void shouldThrowVersionConflictWhenInterruptCasFails() {
        CoachingRun running = runningRun();

        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(Optional.of(running));
        when(repository.updateIfVersionMatches(eq(OWNER), any(CoachingRun.class), eq(2L)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.interruptForActor(
                OWNER,
                RUN_ID,
                "RUN_EXECUTION_INTERRUPTED"
        )).isInstanceOf(CoachingRunVersionConflictException.class)
                .hasMessage("Run版本已经发生变化");
    }

    private CoachingRun receivedRun() {
        return CoachingRun.receive(
                RUN_ID,
                OWNER,
                SESSION_ID,
                REQUEST_ID,
                "a".repeat(64),
                4L,
                CREATED_AT
        );
    }

    private CoachingRun acceptedRun() {
        return receivedRun().accept(
                USER_TURN_ID,
                CREATED_AT.plusSeconds(10)
        );
    }

    private CoachingRun runningRun() {
        return acceptedRun().start(CREATED_AT.plusSeconds(20));
    }
}