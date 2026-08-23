package com.leo.careerforgeai.agent.api.sse;

import com.leo.careerforgeai.agent.api.dto.CoachingRunHeartbeatResponse;
import com.leo.careerforgeai.agent.api.dto.CoachingRunSseEventResponse;
import com.leo.careerforgeai.agent.api.dto.CoachingRunSseEventSource;
import com.leo.careerforgeai.agent.api.dto.CoachingRunSseEventType;
import com.leo.careerforgeai.agent.application.run.event.CoachingRunEventObservation;
import com.leo.careerforgeai.agent.application.run.event.CoachingRunEventQueryApplicationService;
import com.leo.careerforgeai.agent.application.run.event.CoachingRunEventType;
import com.leo.careerforgeai.agent.application.run.event.StoredCoachingRunEvent;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureErrorType;
import com.leo.careerforgeai.shared.actor.ActorId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @program: CareerForge-AI
 * @description: 使用专用虚拟线程发送Run事件、心跳、断线续读和MySQL终态快照
 * @author: Miao Zheng
 * @date: 2026-08-21
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CoachingRunSseService {

    private static final Duration CONNECTION_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
    private static final int BATCH_SIZE = 100;

    private final CoachingRunEventQueryApplicationService queryService;
    private final ExecutorService observerExecutor;
    private final Clock clock;

    public CoachingRunSseService(
            CoachingRunEventQueryApplicationService queryService,
            @Qualifier("coachingRunSseExecutor") ExecutorService observerExecutor,
            Clock agentClock
    ) {
        this.queryService = Objects.requireNonNull(queryService, "queryService不能为空");
        this.observerExecutor = Objects.requireNonNull(observerExecutor, "observerExecutor不能为空");
        this.clock = Objects.requireNonNull(agentClock, "agentClock不能为空");
    }

    public SseEmitter open(UUID runId, String lastEventId) {
        Objects.requireNonNull(runId, "runId不能为空");

        CoachingRunEventObservation initial = queryService.observe(runId, lastEventId, BATCH_SIZE);
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT.toMillis());
        AtomicBoolean closed = new AtomicBoolean();

        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> {
            closed.set(true);
            emitter.complete();
        });
        emitter.onError(exception -> closed.set(true));

        observerExecutor.execute(() -> stream(emitter, closed, initial, lastEventId));
        return emitter;
    }

    private void stream(
            SseEmitter emitter,
            AtomicBoolean closed,
            CoachingRunEventObservation initial,
            String lastEventId
    ) {
        ActorId ownerId = initial.run().ownerId();
        UUID runId = initial.run().runId();
        String cursor = lastEventId;
        Instant nextHeartbeat = clock.instant().plus(HEARTBEAT_INTERVAL);
        RedisInfrastructureErrorType previousRedisError = null;
        CoachingRunStatus deliveredTerminalStatus = null;
        CoachingRunEventObservation observation = initial;

        try {
            while (!closed.get() && !Thread.currentThread().isInterrupted()) {
                previousRedisError = logRedisState(runId, previousRedisError, observation.redisErrorType());

                for (StoredCoachingRunEvent event : observation.events()) {
                    sendStoredEvent(emitter, event);
                    cursor = event.eventId();
                    if (event.type().isTerminal()) deliveredTerminalStatus = event.status();                }

                CoachingRun run = observation.run();
                if (run.isTerminal()) {
                    if (deliveredTerminalStatus != run.status()) sendMysqlTerminalSnapshot(emitter, run);
                    emitter.complete();
                    return;
                }

                if (observation.events().size() < BATCH_SIZE) {
                    Instant now = clock.instant();
                    if (!now.isBefore(nextHeartbeat)) {
                        sendHeartbeat(emitter, runId, now);
                        nextHeartbeat = now.plus(HEARTBEAT_INTERVAL);
                    }
                    Thread.sleep(POLL_INTERVAL.toMillis());
                }

                observation = queryService.observeForActor(ownerId, runId, cursor, BATCH_SIZE);
            }
            emitter.complete();
        } catch (IOException exception) {
            log.debug("Run SSE连接已经断开，runId={}", runId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (RuntimeException exception) {
            log.warn("Run SSE观察终止，runId={}, errorType={}", runId, exception.getClass().getSimpleName());
            emitter.complete();
        } finally {
            closed.set(true);
        }
    }

    private RedisInfrastructureErrorType logRedisState(
            UUID runId,
            RedisInfrastructureErrorType previous,
            RedisInfrastructureErrorType current
    ) {
        if (current != null && current != previous) {
            log.warn("Run SSE暂时无法读取Redis事件，runId={}, errorType={}", runId, current);
        } else if (current == null && previous != null) {
            log.info("Run SSE已恢复读取Redis事件，runId={}", runId);
        }
        return current;
    }

    private void sendStoredEvent(SseEmitter emitter, StoredCoachingRunEvent event) throws IOException {
        CoachingRunSseEventResponse response = new CoachingRunSseEventResponse(
                event.runId(),
                event.type(),
                event.status(),
                event.toolName(),
                event.toolStatus(),
                CoachingRunSseEventSource.REDIS_STREAM,
                event.occurredAt()
        );
        emitter.send(
                SseEmitter.event()
                        .id(event.eventId())
                        .name(CoachingRunSseEventType.fromEventType(event.type()).name())
                        .data(response)
        );
    }

    private void sendMysqlTerminalSnapshot(SseEmitter emitter, CoachingRun run) throws IOException {
        if (!run.isTerminal()) throw new IllegalArgumentException("只有终态Run可以发送MySQL终态快照");

        CoachingRunEventType type = CoachingRunEventType.fromStatus(run.status());
        CoachingRunSseEventResponse response = new CoachingRunSseEventResponse(
                run.runId(),
                type,
                run.status(),
                null,
                null,
                CoachingRunSseEventSource.MYSQL_TERMINAL_SNAPSHOT,
                run.updatedAt()
        );
        emitter.send(
                SseEmitter.event()
                        .name(CoachingRunSseEventType.fromEventType(type).name())
                        .data(response)
        );
    }

    private void sendHeartbeat(SseEmitter emitter, UUID runId, Instant sentAt) throws IOException {
        emitter.send(
                SseEmitter.event()
                        .name(CoachingRunSseEventType.HEARTBEAT.name())
                        .data(new CoachingRunHeartbeatResponse(runId, sentAt))
        );
    }
}