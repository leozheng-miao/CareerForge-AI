package com.leo.careerforgeai.agent.application.run.event;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunEventStore;
import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.application.run.CoachingRunNotFoundException;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureErrorType;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证Run事件查询的owner隔离、断线续读、Redis降级和多观察者语义
 * @author: Miao Zheng
 * @date: 2026-08-23
 */
@ExtendWith(MockitoExtension.class)
class CoachingRunEventQueryApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final ActorId OTHER_OWNER = new ActorId("actor-b");
    private static final UUID RUN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID USER_TURN_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ASSISTANT_TURN_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-23T08:00:00Z");
    private static final String LAST_EVENT_ID = "1724400000000-0";
    private static final String NEXT_EVENT_ID = "1724400000001-0";

    @Mock
    private CurrentActorProvider currentActorProvider;

    @Mock
    private CoachingRunRepository repository;

    @Mock
    private CoachingRunEventStore eventStore;

    private CoachingRunEventQueryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CoachingRunEventQueryApplicationService(currentActorProvider, repository, eventStore);
    }

    @Test
    void shouldValidateOwnerAndPassLastEventIdToEventStore() {
        CoachingRun run = runningRun();
        StoredCoachingRunEvent event = runningEvent();
        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(Optional.of(run));
        when(eventStore.readAfter(OWNER, RUN_ID, LAST_EVENT_ID, 100)).thenReturn(List.of(event));

        CoachingRunEventObservation observation = service.observe(RUN_ID, LAST_EVENT_ID, 100);

        assertSame(run, observation.run());
        assertEquals(List.of(event), observation.events());
        assertTrue(observation.redisAvailable());
        verify(eventStore).readAfter(OWNER, RUN_ID, LAST_EVENT_ID, 100);
    }

    @Test
    void shouldRejectCrossOwnerSubscriptionBeforeReadingRedis() {
        when(currentActorProvider.currentActor()).thenReturn(OTHER_OWNER);
        when(repository.findByRunId(OTHER_OWNER, RUN_ID)).thenReturn(Optional.empty());

        CoachingRunNotFoundException exception = assertThrows(
                CoachingRunNotFoundException.class,
                () -> service.observe(RUN_ID, LAST_EVENT_ID, 100)
        );

        assertEquals("Run不存在或不属于当前用户", exception.getMessage());
        verifyNoInteractions(eventStore);
    }

    @Test
    void shouldKeepMysqlTerminalFactWhenRedisIsUnavailable() {
        CoachingRun run = succeededRun();
        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(Optional.of(run));
        when(eventStore.readAfter(OWNER, RUN_ID, LAST_EVENT_ID, 100))
                .thenThrow(new RedisInfrastructureException(
                        RedisInfrastructureErrorType.UNAVAILABLE,
                        "Redis不可用"
                ));

        CoachingRunEventObservation observation = service.observe(RUN_ID, LAST_EVENT_ID, 100);

        assertSame(run, observation.run());
        assertTrue(observation.run().isTerminal());
        assertTrue(observation.events().isEmpty());
        assertEquals(RedisInfrastructureErrorType.UNAVAILABLE, observation.redisErrorType());
    }

    @Test
    void shouldKeepMysqlTerminalFactWhenRedisHistoryHasExpired() {
        CoachingRun run = succeededRun();
        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(Optional.of(run));
        when(eventStore.readAfter(OWNER, RUN_ID, LAST_EVENT_ID, 100)).thenReturn(List.of());

        CoachingRunEventObservation observation = service.observe(RUN_ID, LAST_EVENT_ID, 100);

        assertTrue(observation.run().isTerminal());
        assertEquals(CoachingRunStatus.SUCCEEDED, observation.run().status());
        assertTrue(observation.events().isEmpty());
        assertTrue(observation.redisAvailable());
    }

    @Test
    void shouldAllowMultipleObserversToReadSameEventsIndependently() {
        CoachingRun run = runningRun();
        StoredCoachingRunEvent event = runningEvent();
        when(repository.findByRunId(OWNER, RUN_ID)).thenReturn(Optional.of(run));
        when(eventStore.readAfter(OWNER, RUN_ID, LAST_EVENT_ID, 100)).thenReturn(List.of(event));

        CoachingRunEventObservation first = service.observeForActor(OWNER, RUN_ID, LAST_EVENT_ID, 100);
        CoachingRunEventObservation second = service.observeForActor(OWNER, RUN_ID, LAST_EVENT_ID, 100);

        assertEquals(first.events(), second.events());
        verify(eventStore, times(2)).readAfter(OWNER, RUN_ID, LAST_EVENT_ID, 100);
    }

    private CoachingRun runningRun() {
        return CoachingRun.receive(
                RUN_ID,
                OWNER,
                SESSION_ID,
                REQUEST_ID,
                "a".repeat(64),
                4L,
                NOW
        ).accept(USER_TURN_ID, NOW.plusSeconds(1))
                .start(NOW.plusSeconds(2));
    }

    private CoachingRun succeededRun() {
        return runningRun().succeed(ASSISTANT_TURN_ID, NOW.plusSeconds(3));
    }

    private StoredCoachingRunEvent runningEvent() {
        return new StoredCoachingRunEvent(
                NEXT_EVENT_ID,
                RUN_ID,
                CoachingRunEventType.RUN_STARTED,
                CoachingRunStatus.RUNNING,
                null,
                null,
                NOW.plusSeconds(2)
        );
    }
}