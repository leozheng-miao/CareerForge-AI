package com.leo.careerforgeai.agent.application.run.event;

import com.leo.careerforgeai.agent.api.dto.CoachingRunSseEventResponse;
import com.leo.careerforgeai.agent.api.dto.CoachingRunSseEventSource;
import com.leo.careerforgeai.agent.application.port.run.CoachingRunEventStore;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.agent.infrastructure.persistence.adapter.EventPublishingCoachingRunRepository;
import com.leo.careerforgeai.agent.infrastructure.persistence.adapter.MyBatisPlusCoachingRunAdapter;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureErrorType;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import com.leo.careerforgeai.agent.infrastructure.redis.event.RedisCoachingRunEventListener;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证MySQL提交事件、Redis安全事件顺序、失败隔离和事件白名单
 * @author: Miao Zheng
 * @date: 2026-08-23
 */
@ExtendWith(MockitoExtension.class)
class CoachingRunEventPipelineTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID RUN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_RUN_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID SESSION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_REQUEST_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID USER_TURN_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-23T08:00:00Z");

    @Mock
    private MyBatisPlusCoachingRunAdapter delegate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CoachingRunEventStore eventStore;

    @Test
    void shouldPublishReceivedEventOnlyForNewClaim() {
        CoachingRun candidate = receivedRun(RUN_ID, REQUEST_ID);
        CoachingRun replayCandidate = receivedRun(OTHER_RUN_ID, OTHER_REQUEST_ID);
        when(delegate.claim(candidate)).thenReturn(candidate);
        when(delegate.claim(replayCandidate)).thenReturn(candidate);

        EventPublishingCoachingRunRepository repository =
                new EventPublishingCoachingRunRepository(delegate, eventPublisher);

        repository.claim(candidate);
        repository.claim(replayCandidate);

        ArgumentCaptor<CoachingRunEvent> captor = ArgumentCaptor.forClass(CoachingRunEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue().runId()).isEqualTo(RUN_ID);
        assertThat(captor.getValue().type()).isEqualTo(CoachingRunEventType.RUN_RECEIVED);
        assertThat(captor.getValue().status()).isEqualTo(CoachingRunStatus.RECEIVED);
    }

    @Test
    void shouldPublishStateEventOnlyAfterSuccessfulCas() {
        CoachingRun accepted = receivedRun(RUN_ID, REQUEST_ID).accept(USER_TURN_ID, NOW.plusSeconds(1));
        when(delegate.updateIfVersionMatches(OWNER, accepted, 0L)).thenReturn(true);

        EventPublishingCoachingRunRepository repository =
                new EventPublishingCoachingRunRepository(delegate, eventPublisher);

        boolean updated = repository.updateIfVersionMatches(OWNER, accepted, 0L);

        assertThat(updated).isTrue();
        ArgumentCaptor<CoachingRunEvent> captor = ArgumentCaptor.forClass(CoachingRunEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(CoachingRunEventType.RUN_ACCEPTED);
        assertThat(captor.getValue().status()).isEqualTo(CoachingRunStatus.ACCEPTED);
    }

    @Test
    void shouldNotPublishEventWhenCasFails() {
        CoachingRun accepted = receivedRun(RUN_ID, REQUEST_ID).accept(USER_TURN_ID, NOW.plusSeconds(1));
        when(delegate.updateIfVersionMatches(OWNER, accepted, 0L)).thenReturn(false);

        EventPublishingCoachingRunRepository repository =
                new EventPublishingCoachingRunRepository(delegate, eventPublisher);

        boolean updated = repository.updateIfVersionMatches(OWNER, accepted, 0L);

        assertThat(updated).isFalse();
        verify(eventPublisher, never()).publishEvent(any(CoachingRunEvent.class));
    }

    @Test
    void shouldAppendAnswerReadyBeforeRunCompleted() {
        CoachingRunEvent completed = CoachingRunEvent.runState(
                OWNER,
                RUN_ID,
                CoachingRunStatus.SUCCEEDED,
                NOW
        );
        when(eventStore.append(any(CoachingRunEvent.class))).thenReturn("100-0", "101-0");

        RedisCoachingRunEventListener listener = new RedisCoachingRunEventListener(eventStore);
        listener.onCommitted(completed);

        ArgumentCaptor<CoachingRunEvent> captor = ArgumentCaptor.forClass(CoachingRunEvent.class);
        verify(eventStore, times(2)).append(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(CoachingRunEvent::type)
                .containsExactly(
                        CoachingRunEventType.ANSWER_READY,
                        CoachingRunEventType.RUN_COMPLETED
                );
    }

    @Test
    void shouldNotAppendCompletedWhenAnswerReadyAppendFails() {
        CoachingRunEvent completed = CoachingRunEvent.runState(
                OWNER,
                RUN_ID,
                CoachingRunStatus.SUCCEEDED,
                NOW
        );
        when(eventStore.append(any(CoachingRunEvent.class)))
                .thenThrow(new RedisInfrastructureException(
                        RedisInfrastructureErrorType.UNAVAILABLE,
                        "Redis不可用"
                ));

        RedisCoachingRunEventListener listener = new RedisCoachingRunEventListener(eventStore);

        assertThatCode(() -> listener.onCommitted(completed)).doesNotThrowAnyException();
        verify(eventStore, times(1)).append(any(CoachingRunEvent.class));
    }

    @Test
    void shouldRejectUnsafeToolNameAndInvalidMysqlSnapshot() {
        assertThatThrownBy(() -> CoachingRunEvent.toolStarted(
                OWNER,
                RUN_ID,
                "search tool",
                NOW
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolName格式不合法");

        assertThatThrownBy(() -> new CoachingRunSseEventResponse(
                RUN_ID,
                CoachingRunEventType.RUN_STARTED,
                CoachingRunStatus.RUNNING,
                null,
                null,
                CoachingRunSseEventSource.MYSQL_TERMINAL_SNAPSHOT,
                NOW
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("MySQL快照只能发送终态事件");
    }

    private CoachingRun receivedRun(UUID runId, UUID requestId) {
        return CoachingRun.receive(
                runId,
                OWNER,
                SESSION_ID,
                requestId,
                "a".repeat(64),
                4L,
                NOW
        );
    }
}