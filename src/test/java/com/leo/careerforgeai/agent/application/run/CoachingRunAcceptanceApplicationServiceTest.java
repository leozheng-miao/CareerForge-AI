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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证保存USER Turn、Run接受、幂等重放、指纹冲突和CAS冲突
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@ExtendWith(MockitoExtension.class)
class CoachingRunAcceptanceApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID USER_TURN_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID EXCHANGE_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-20T03:00:00Z");
    private static final String MESSAGE = "请解释Java并发";
    private static final long EXPECTED_SESSION_VERSION = 4L;

    @Mock
    private CurrentActorProvider currentActorProvider;

    @Mock
    private CoachingRunRepository repository;

    @Mock
    private CoachingSessionApplicationService sessionApplicationService;

    private CoachingRunRequestFingerprintService fingerprintService;
    private CoachingRunAcceptanceApplicationService service;

    @BeforeEach
    void setUp() {
        fingerprintService = new CoachingRunRequestFingerprintService();
        service = new CoachingRunAcceptanceApplicationService(
                currentActorProvider,
                repository,
                fingerprintService,
                sessionApplicationService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(currentActorProvider.currentActor()).thenReturn(OWNER);
    }

    @Test
    void shouldSaveUserTurnAndAcceptReceivedRun() {
        CoachingRun received = receivedRun();
        ConversationTurn userTurn = userTurn();

        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(java.util.Optional.of(received));
        when(sessionApplicationService.recordUserTurn(
                SESSION_ID,
                EXPECTED_SESSION_VERSION,
                MESSAGE
        )).thenReturn(userTurn);
        when(repository.updateIfVersionMatches(eq(OWNER), any(CoachingRun.class), eq(0L)))
                .thenReturn(true);

        CoachingRun accepted = service.accept(RUN_ID, MESSAGE);

        assertThat(accepted.status()).isEqualTo(CoachingRunStatus.ACCEPTED);
        assertThat(accepted.userTurnId()).isEqualTo(USER_TURN_ID);
        assertThat(accepted.acceptedAt()).isEqualTo(NOW);
        assertThat(accepted.updatedAt()).isEqualTo(NOW);
        assertThat(accepted.version()).isEqualTo(1L);

        ArgumentCaptor<CoachingRun> captor = ArgumentCaptor.forClass(CoachingRun.class);
        verify(repository).updateIfVersionMatches(eq(OWNER), captor.capture(), eq(0L));

        CoachingRun persisted = captor.getValue();
        assertThat(persisted.runId()).isEqualTo(RUN_ID);
        assertThat(persisted.ownerId()).isEqualTo(OWNER);
        assertThat(persisted.status()).isEqualTo(CoachingRunStatus.ACCEPTED);
        assertThat(persisted.userTurnId()).isEqualTo(USER_TURN_ID);
    }

    @Test
    void shouldReplayAcceptedRunWithoutSavingAnotherUserTurn() {
        CoachingRun accepted = receivedRun().accept(USER_TURN_ID, NOW.minusSeconds(5));
        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(java.util.Optional.of(accepted));

        CoachingRun replayed = service.accept(RUN_ID, MESSAGE);

        assertThat(replayed).isSameAs(accepted);
        verifyNoInteractions(sessionApplicationService);
        verify(repository, never()).updateIfVersionMatches(any(), any(), anyLong());
    }

    @Test
    void shouldRejectDifferentMessageBeforeSavingUserTurn() {
        CoachingRun received = receivedRun();
        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(java.util.Optional.of(received));

        assertThatThrownBy(() -> service.accept(RUN_ID, "另一条消息"))
                .isInstanceOf(CoachingRunRequestConflictException.class)
                .hasMessage("requestId已被用于不同请求");

        verifyNoInteractions(sessionApplicationService);
        verify(repository, never()).updateIfVersionMatches(any(), any(), anyLong());
    }

    @Test
    void shouldThrowVersionConflictWhenRunCasFails() {
        CoachingRun received = receivedRun();

        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(java.util.Optional.of(received));
        when(sessionApplicationService.recordUserTurn(
                SESSION_ID,
                EXPECTED_SESSION_VERSION,
                MESSAGE
        )).thenReturn(userTurn());
        when(repository.updateIfVersionMatches(eq(OWNER), any(CoachingRun.class), eq(0L)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.accept(RUN_ID, MESSAGE))
                .isInstanceOf(CoachingRunVersionConflictException.class)
                .hasMessage("Run版本已经发生变化")
                .satisfies(exception -> {
                    CoachingRunVersionConflictException conflict =
                            (CoachingRunVersionConflictException) exception;
                    assertThat(conflict.runId()).isEqualTo(RUN_ID);
                    assertThat(conflict.expectedVersion()).isZero();
                });
    }

    private CoachingRun receivedRun() {
        return CoachingRun.receive(
                RUN_ID,
                OWNER,
                SESSION_ID,
                REQUEST_ID,
                fingerprintService.fingerprint(SESSION_ID, EXPECTED_SESSION_VERSION, MESSAGE),
                EXPECTED_SESSION_VERSION,
                NOW.minusSeconds(10)
        );
    }

    private ConversationTurn userTurn() {
        return ConversationTurn.completedUser(
                USER_TURN_ID,
                SESSION_ID,
                EXCHANGE_ID,
                OWNER,
                EXPECTED_SESSION_VERSION + 1,
                MESSAGE,
                NOW
        );
    }
}