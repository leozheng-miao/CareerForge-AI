package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证应用启动后分批恢复遗留Run及失败时停止重复扫描
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@ExtendWith(MockitoExtension.class)
class CoachingRunStartupRecoveryTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID_A = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID_B = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID USER_TURN_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-20T14:00:00Z");

    @Mock
    private CoachingRunRepository repository;

    @Mock
    private CoachingRunInterruptionApplicationService interruptionService;

    private CoachingRunStartupRecovery recovery;

    @BeforeEach
    void setUp() {
        recovery = new CoachingRunStartupRecovery(
                repository,
                interruptionService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldRecoverNonTerminalRunsInBatches() {
        CoachingRun accepted = acceptedRun(RUN_ID_A);
        CoachingRun running = runningRun(RUN_ID_B);

        when(repository.findNonTerminalUpdatedBefore(NOW, 100))
                .thenReturn(
                        List.of(accepted, running),
                        List.of()
                );

        recovery.recoverInterruptedRuns();

        InOrder order = inOrder(interruptionService);
        order.verify(interruptionService).interruptForActor(
                OWNER,
                RUN_ID_A,
                "APPLICATION_RESTARTED"
        );
        order.verify(interruptionService).interruptForActor(
                OWNER,
                RUN_ID_B,
                "APPLICATION_RESTARTED"
        );
        verify(repository, times(2))
                .findNonTerminalUpdatedBefore(NOW, 100);
    }

    @Test
    void shouldContinueWhenOneCandidateHasVersionConflict() {
        CoachingRun accepted = acceptedRun(RUN_ID_A);
        CoachingRun running = runningRun(RUN_ID_B);

        when(repository.findNonTerminalUpdatedBefore(NOW, 100))
                .thenReturn(
                        List.of(accepted, running),
                        List.of()
                );
        when(interruptionService.interruptForActor(
                OWNER,
                RUN_ID_A,
                "APPLICATION_RESTARTED"
        )).thenThrow(new CoachingRunVersionConflictException(RUN_ID_A, 1L));

        recovery.recoverInterruptedRuns();

        verify(interruptionService).interruptForActor(
                OWNER,
                RUN_ID_B,
                "APPLICATION_RESTARTED"
        );
        verify(repository, times(2))
                .findNonTerminalUpdatedBefore(NOW, 100);
    }

    @Test
    void shouldStopWhenEntireBatchCannotBeRecovered() {
        CoachingRun running = runningRun(RUN_ID_B);

        when(repository.findNonTerminalUpdatedBefore(NOW, 100))
                .thenReturn(List.of(running));
        when(interruptionService.interruptForActor(
                OWNER,
                RUN_ID_B,
                "APPLICATION_RESTARTED"
        )).thenThrow(new IllegalStateException("数据库暂时不可用"));

        recovery.recoverInterruptedRuns();

        verify(repository).findNonTerminalUpdatedBefore(NOW, 100);
        verify(interruptionService).interruptForActor(
                OWNER,
                RUN_ID_B,
                "APPLICATION_RESTARTED"
        );
    }

    private CoachingRun acceptedRun(UUID runId) {
        return CoachingRun.receive(
                runId,
                OWNER,
                SESSION_ID,
                requestId(runId),
                "a".repeat(64),
                4L,
                NOW.minusSeconds(30)
        ).accept(USER_TURN_ID, NOW.minusSeconds(20));
    }

    private CoachingRun runningRun(UUID runId) {
        return acceptedRun(runId).start(NOW.minusSeconds(10));
    }

    private UUID requestId(UUID runId) {
        return runId.equals(RUN_ID_A)
                ? REQUEST_ID
                : UUID.fromString("20000000-0000-0000-0000-000000000002");
    }
}