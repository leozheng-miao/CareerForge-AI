package com.leo.careerforgeai.agent.evaluation.recovery;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.application.run.lifecycle.CoachingRunInterruptionApplicationService;
import com.leo.careerforgeai.agent.application.run.lifecycle.CoachingRunStartupRecovery;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证应用重启时RECEIVED、ACCEPTED和RUNNING遗留Run全部收敛
 * @author: Miao Zheng
 * @date: 2026-08-25
 */
class CoachingRunRecoveryStateMatrixTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID SESSION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RECEIVED_RUN_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ACCEPTED_RUN_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID RUNNING_RUN_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID ACCEPTED_TURN_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID RUNNING_TURN_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-08-25T13:00:00Z");

    @Test
    void shouldRecoverEveryNonTerminalRunState() {
        CoachingRunRepository repository =
                mock(CoachingRunRepository.class);
        CoachingRunInterruptionApplicationService interruptionService =
                mock(CoachingRunInterruptionApplicationService.class);

        CoachingRun received = receivedRun(
                RECEIVED_RUN_ID,
                "20000000-0000-0000-0000-000000000001"
        );
        CoachingRun accepted = receivedRun(
                ACCEPTED_RUN_ID,
                "20000000-0000-0000-0000-000000000002"
        ).accept(
                ACCEPTED_TURN_ID,
                NOW.minusSeconds(20)
        );
        CoachingRun running = receivedRun(
                RUNNING_RUN_ID,
                "20000000-0000-0000-0000-000000000003"
        ).accept(
                RUNNING_TURN_ID,
                NOW.minusSeconds(20)
        ).start(
                NOW.minusSeconds(10)
        );

        when(repository.findNonTerminalUpdatedBefore(NOW, 100))
                .thenReturn(
                        List.of(received, accepted, running),
                        List.of()
                );

        CoachingRunStartupRecovery recovery =
                new CoachingRunStartupRecovery(
                        repository,
                        interruptionService,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );

        recovery.recoverInterruptedRuns();

        InOrder order = inOrder(interruptionService);
        order.verify(interruptionService).interruptForActor(
                OWNER,
                RECEIVED_RUN_ID,
                "APPLICATION_RESTARTED"
        );
        order.verify(interruptionService).interruptForActor(
                OWNER,
                ACCEPTED_RUN_ID,
                "APPLICATION_RESTARTED"
        );
        order.verify(interruptionService).interruptForActor(
                OWNER,
                RUNNING_RUN_ID,
                "APPLICATION_RESTARTED"
        );

        verify(repository, times(2))
                .findNonTerminalUpdatedBefore(NOW, 100);
    }

    private CoachingRun receivedRun(
            UUID runId,
            String requestId
    ) {
        return CoachingRun.receive(
                runId,
                OWNER,
                SESSION_ID,
                UUID.fromString(requestId),
                "a".repeat(64),
                4,
                NOW.minusSeconds(30)
        );
    }
}