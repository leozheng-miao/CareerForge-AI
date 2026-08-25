package com.leo.careerforgeai.agent.evaluation.security;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunEventStore;
import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.application.run.CoachingRunApplicationService;
import com.leo.careerforgeai.agent.application.run.CoachingRunNotFoundException;
import com.leo.careerforgeai.agent.application.run.event.CoachingRunEventQueryApplicationService;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunExecutionApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunAcceptanceApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimApplicationService;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证普通Run查询和SSE事件订阅共同执行MySQL owner隔离
 * @author: Miao Zheng
 * @date: 2026-08-25
 */
class CoachingRunCrossOwnerAccessMatrixTest {

    private static final ActorId OTHER_OWNER = new ActorId("actor-b");
    private static final UUID RUN_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void shouldHideRunFromCrossOwnerQueryAndSseSubscription() {
        CurrentActorProvider actorProvider =
                mock(CurrentActorProvider.class);
        CoachingRunRepository repository =
                mock(CoachingRunRepository.class);
        CoachingRunEventStore eventStore =
                mock(CoachingRunEventStore.class);

        when(actorProvider.currentActor()).thenReturn(OTHER_OWNER);
        when(repository.findByRunId(OTHER_OWNER, RUN_ID))
                .thenReturn(Optional.empty());

        CoachingRunApplicationService runService =
                new CoachingRunApplicationService(
                        actorProvider,
                        repository,
                        mock(CoachingRunClaimApplicationService.class),
                        mock(CoachingRunAcceptanceApplicationService.class),
                        mock(CoachingRunExecutionApplicationService.class)
                );
        CoachingRunEventQueryApplicationService eventService =
                new CoachingRunEventQueryApplicationService(
                        actorProvider,
                        repository,
                        eventStore
                );

        assertThatThrownBy(() -> runService.get(RUN_ID))
                .isInstanceOf(CoachingRunNotFoundException.class)
                .hasMessage("Run不存在或不属于当前用户");

        assertThatThrownBy(() ->
                eventService.observe(RUN_ID, "100-0", 100)
        ).isInstanceOf(CoachingRunNotFoundException.class)
                .hasMessage("Run不存在或不属于当前用户");

        verifyNoInteractions(eventStore);
    }
}