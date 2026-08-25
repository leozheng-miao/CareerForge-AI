package com.leo.careerforgeai.agent.evaluation.concurrency;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.application.run.lifecycle.CoachingRunLifecycleApplicationService;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.memory.application.conversation.CoachingSessionApplicationService;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证条件拒绝只终结RECEIVED Run并保留其他线程已推进的状态
 * @author: Miao Zheng
 * @date: 2026-08-25
 */
class CoachingRunReceivedRejectionTest {

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

    @Test
    void shouldRejectRunStillInReceivedState() {
        CoachingRunRepository repository =
                mock(CoachingRunRepository.class);
        CoachingRun received = receivedRun();

        when(repository.findByRunId(OWNER, RUN_ID))
                .thenReturn(Optional.of(received));
        when(repository.updateIfVersionMatches(
                eq(OWNER),
                any(CoachingRun.class),
                eq(0L)
        )).thenReturn(true);

        CoachingRunLifecycleApplicationService service =
                service(repository);

        CoachingRun rejected = service.rejectReceivedForActor(
                OWNER,
                RUN_ID,
                "SESSION_VERSION_DRIFT"
        );

        assertThat(rejected.status())
                .isEqualTo(CoachingRunStatus.REJECTED);
        assertThat(rejected.failureCode())
                .isEqualTo("SESSION_VERSION_DRIFT");
        assertThat(rejected.version()).isEqualTo(1);

        verify(repository).updateIfVersionMatches(
                OWNER,
                rejected,
                0
        );
    }

    @Test
    void shouldPreserveRunAlreadyAcceptedByAnotherThread() {
        CoachingRunRepository repository =
                mock(CoachingRunRepository.class);
        CoachingRun accepted = receivedRun().accept(
                USER_TURN_ID,
                NOW.minusSeconds(1)
        );

        when(repository.findByRunId(OWNER, RUN_ID))
                .thenReturn(Optional.of(accepted));

        CoachingRunLifecycleApplicationService service =
                service(repository);

        CoachingRun result = service.rejectReceivedForActor(
                OWNER,
                RUN_ID,
                "SESSION_VERSION_DRIFT"
        );

        assertThat(result).isSameAs(accepted);
        assertThat(result.status())
                .isEqualTo(CoachingRunStatus.ACCEPTED);
        verify(repository, never()).updateIfVersionMatches(
                any(),
                any(),
                any(Long.class)
        );
    }

    private CoachingRunLifecycleApplicationService service(
            CoachingRunRepository repository
    ) {
        CurrentActorProvider actorProvider =
                mock(CurrentActorProvider.class);
        CoachingSessionApplicationService sessionService =
                mock(CoachingSessionApplicationService.class);

        return new CoachingRunLifecycleApplicationService(
                actorProvider,
                repository,
                sessionService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private CoachingRun receivedRun() {
        return CoachingRun.receive(
                RUN_ID,
                OWNER,
                SESSION_ID,
                REQUEST_ID,
                "a".repeat(64),
                4,
                NOW.minusSeconds(10)
        );
    }
}