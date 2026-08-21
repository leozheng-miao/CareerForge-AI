package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunExecutionApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunAcceptanceApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimResult;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证Run新请求同步执行、幂等重放和owner隔离查询
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@ExtendWith(MockitoExtension.class)
class CoachingRunApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID USER_TURN_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ASSISTANT_TURN_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-20T06:00:00Z");
    private static final String MESSAGE = "请解释Java并发";

    @Mock
    private CurrentActorProvider currentActorProvider;

    @Mock
    private CoachingRunRepository repository;

    @Mock
    private CoachingRunClaimApplicationService claimService;

    @Mock
    private CoachingRunAcceptanceApplicationService acceptanceService;

    @Mock
    private CoachingRunExecutionApplicationService executionService;

    private CoachingRunApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CoachingRunApplicationService(
                currentActorProvider,
                repository,
                claimService,
                acceptanceService,
                executionService
        );
    }

    @Test
    void shouldClaimAcceptAndExecuteNewRun() {
        CoachingRun received = receivedRun();
        CoachingRun accepted = received.accept(USER_TURN_ID, NOW.plusSeconds(1));
        CoachingRun succeeded = accepted.start(NOW.plusSeconds(2))
                .succeed(ASSISTANT_TURN_ID, NOW.plusSeconds(3));

        when(claimService.claim(SESSION_ID, REQUEST_ID, 4L, MESSAGE))
                .thenReturn(new CoachingRunClaimResult(received, false));
        when(acceptanceService.accept(RUN_ID, MESSAGE)).thenReturn(accepted);
        when(executionService.execute(RUN_ID)).thenReturn(succeeded);

        CoachingRun result = service.submit(
                SESSION_ID,
                REQUEST_ID,
                4L,
                MESSAGE
        );

        assertThat(result).isSameAs(succeeded);
    }

    @Test
    void shouldReplayExistingRunWithoutAnotherSideEffect() {
        CoachingRun existing = receivedRun().accept(USER_TURN_ID, NOW.plusSeconds(1));
        when(claimService.claim(SESSION_ID, REQUEST_ID, 4L, MESSAGE))
                .thenReturn(new CoachingRunClaimResult(existing, true));

        CoachingRun result = service.submit(
                SESSION_ID,
                REQUEST_ID,
                4L,
                MESSAGE
        );

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(acceptanceService, executionService);
    }

    @Test
    void shouldQueryOwnedRun() {
        CoachingRun run = receivedRun();
        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(Optional.of(run));

        assertThat(service.get(RUN_ID)).isSameAs(run);
    }

    @Test
    void shouldHideMissingOrCrossOwnerRun() {
        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(RUN_ID))
                .isInstanceOf(CoachingRunNotFoundException.class)
                .hasMessage("Run不存在或不属于当前用户");
    }

    private CoachingRun receivedRun() {
        return CoachingRun.receive(
                RUN_ID,
                OWNER,
                SESSION_ID,
                REQUEST_ID,
                "a".repeat(64),
                4L,
                NOW
        );
    }
}