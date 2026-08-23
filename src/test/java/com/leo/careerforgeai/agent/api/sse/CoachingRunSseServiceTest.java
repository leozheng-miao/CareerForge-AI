package com.leo.careerforgeai.agent.api.sse;

import com.leo.careerforgeai.agent.api.dto.CoachingRunHeartbeatResponse;
import com.leo.careerforgeai.agent.api.dto.CoachingRunSseEventResponse;
import com.leo.careerforgeai.agent.api.dto.CoachingRunSseEventSource;
import com.leo.careerforgeai.agent.application.run.event.CoachingRunEventObservation;
import com.leo.careerforgeai.agent.application.run.event.CoachingRunEventQueryApplicationService;
import com.leo.careerforgeai.agent.application.run.event.CoachingRunEventType;
import com.leo.careerforgeai.agent.application.run.event.StoredCoachingRunEvent;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证SSE事件ID、心跳、MySQL终态收敛、终态去重和客户端断线语义
 * @author: Miao Zheng
 * @date: 2026-08-23
 */
@ExtendWith(MockitoExtension.class)
class CoachingRunSseServiceTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID RUN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID USER_TURN_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ASSISTANT_TURN_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-23T08:00:00Z");
    private static final String LAST_EVENT_ID = "1724400000000-0";
    private static final String TERMINAL_EVENT_ID = "1724400000001-0";

    @Mock
    private CoachingRunEventQueryApplicationService queryService;

    @Mock
    private ExecutorService observerExecutor;

    @Mock
    private Clock clock;

    private CoachingRunSseService service;

    @BeforeEach
    void setUp() {
        service = new CoachingRunSseService(queryService, observerExecutor, clock);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(observerExecutor).execute(any(Runnable.class));
    }

    @Test
    void shouldSendRedisTerminalEventWithStreamIdOnlyOnce() throws Exception {
        CoachingRun run = succeededRun();
        StoredCoachingRunEvent event = new StoredCoachingRunEvent(
                TERMINAL_EVENT_ID,
                RUN_ID,
                CoachingRunEventType.RUN_COMPLETED,
                CoachingRunStatus.SUCCEEDED,
                null,
                null,
                NOW.plusSeconds(3)
        );
        when(queryService.observe(RUN_ID, LAST_EVENT_ID, 100))
                .thenReturn(new CoachingRunEventObservation(run, List.of(event), null));
        when(clock.instant()).thenReturn(NOW);

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class)) {
            SseEmitter returned = service.open(RUN_ID, LAST_EVENT_ID);
            SseEmitter emitter = construction.constructed().getFirst();

            assertThat(returned).isSameAs(emitter);
            ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                    ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
            verify(emitter, times(1)).send(captor.capture());
            verify(emitter, times(1)).complete();

            List<Object> parts = eventParts(captor.getValue());
            String metadata = textParts(parts);
            CoachingRunSseEventResponse response = parts.stream()
                    .filter(CoachingRunSseEventResponse.class::isInstance)
                    .map(CoachingRunSseEventResponse.class::cast)
                    .findFirst()
                    .orElseThrow();

            assertThat(metadata).contains("id:" + TERMINAL_EVENT_ID);
            assertThat(metadata).contains("event:RUN_COMPLETED");
            assertThat(response.source()).isEqualTo(CoachingRunSseEventSource.REDIS_STREAM);
            assertThat(response.status()).isEqualTo(CoachingRunStatus.SUCCEEDED);
            verify(queryService, never()).observeForActor(any(), any(), any(), any(Integer.class));
        }
    }

    @Test
    void shouldSendHeartbeatWithoutIdAndFallbackToMysqlTerminalOnce() throws Exception {
        CoachingRun running = runningRun();
        CoachingRun succeeded = succeededRun();
        when(queryService.observe(RUN_ID, LAST_EVENT_ID, 100))
                .thenReturn(new CoachingRunEventObservation(running, List.of(), null));
        when(queryService.observeForActor(OWNER, RUN_ID, LAST_EVENT_ID, 100))
                .thenReturn(new CoachingRunEventObservation(succeeded, List.of(), null));
        when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(10));

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class)) {
            service.open(RUN_ID, LAST_EVENT_ID);
            SseEmitter emitter = construction.constructed().getFirst();

            ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                    ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
            verify(emitter, times(2)).send(captor.capture());
            verify(emitter, times(1)).complete();

            List<Object> heartbeatParts = eventParts(captor.getAllValues().getFirst());
            List<Object> terminalParts = eventParts(captor.getAllValues().get(1));
            String heartbeatMetadata = textParts(heartbeatParts);
            String terminalMetadata = textParts(terminalParts);

            CoachingRunHeartbeatResponse heartbeat = heartbeatParts.stream()
                    .filter(CoachingRunHeartbeatResponse.class::isInstance)
                    .map(CoachingRunHeartbeatResponse.class::cast)
                    .findFirst()
                    .orElseThrow();

            CoachingRunSseEventResponse terminal = terminalParts.stream()
                    .filter(CoachingRunSseEventResponse.class::isInstance)
                    .map(CoachingRunSseEventResponse.class::cast)
                    .findFirst()
                    .orElseThrow();

            assertThat(heartbeatMetadata).contains("event:HEARTBEAT").doesNotContain("id:");
            assertThat(heartbeat.runId()).isEqualTo(RUN_ID);
            assertThat(terminalMetadata).contains("event:RUN_COMPLETED").doesNotContain("id:");
            assertThat(terminal.source()).isEqualTo(CoachingRunSseEventSource.MYSQL_TERMINAL_SNAPSHOT);
            assertThat(terminal.status()).isEqualTo(CoachingRunStatus.SUCCEEDED);
        }
    }

    @Test
    void shouldStopObservationWithoutChangingRunWhenClientDisconnects() throws Exception {
        CoachingRun running = runningRun();
        StoredCoachingRunEvent event = new StoredCoachingRunEvent(
                TERMINAL_EVENT_ID,
                RUN_ID,
                CoachingRunEventType.RUN_STARTED,
                CoachingRunStatus.RUNNING,
                null,
                null,
                NOW.plusSeconds(2)
        );
        when(queryService.observe(RUN_ID, LAST_EVENT_ID, 100))
                .thenReturn(new CoachingRunEventObservation(running, List.of(event), null));
        when(clock.instant()).thenReturn(NOW);

        try (MockedConstruction<SseEmitter> construction = mockConstruction(
                SseEmitter.class,
                (emitter, context) -> doThrow(new IOException("client disconnected"))
                        .when(emitter)
                        .send(any(SseEmitter.SseEventBuilder.class))
        )) {
            service.open(RUN_ID, LAST_EVENT_ID);
            SseEmitter emitter = construction.constructed().getFirst();

            verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
            verify(queryService, never()).observeForActor(any(), any(), any(), any(Integer.class));
            assertThat(running.status()).isEqualTo(CoachingRunStatus.RUNNING);
        }
    }

    private List<Object> eventParts(SseEmitter.SseEventBuilder builder) {
        return builder.build().stream()
                .map(ResponseBodyEmitter.DataWithMediaType::getData)
                .toList();
    }

    private String textParts(List<Object> parts) {
        return parts.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.joining());
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
}