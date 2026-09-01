package com.leo.careerforgeai.interview.api.sse;

import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureErrorType;
import com.leo.careerforgeai.interview.api.dto.event.InterviewHeartbeatResponse;
import com.leo.careerforgeai.interview.api.dto.event.InterviewSseEventResponse;
import com.leo.careerforgeai.interview.application.event.InterviewEventObservation;
import com.leo.careerforgeai.interview.application.event.InterviewEventQueryApplicationService;
import com.leo.careerforgeai.interview.application.event.StoredInterviewEvent;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
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
 * @description: 使用专用虚拟线程发送面试安全事件、心跳、断线续读和MySQL权威状态快照
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@Slf4j
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class InterviewSseService {

    private static final Duration CONNECTION_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
    private static final int BATCH_SIZE = 100;

    private final InterviewEventQueryApplicationService queryService;
    private final ExecutorService observerExecutor;
    private final Clock clock;

    public InterviewSseService(
            InterviewEventQueryApplicationService queryService,
            @Qualifier("interviewSseExecutor") ExecutorService observerExecutor,
            Clock clock
    ) {
        this.queryService = Objects.requireNonNull(queryService, "queryService不能为空");
        this.observerExecutor = Objects.requireNonNull(observerExecutor, "observerExecutor不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    public SseEmitter open(UUID interviewId, String lastEventId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");

        InterviewEventObservation initial = queryService.observe(
                interviewId,
                lastEventId,
                BATCH_SIZE
        );
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT.toMillis());
        AtomicBoolean closed = new AtomicBoolean();

        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> {
            closed.set(true);
            emitter.complete();
        });
        emitter.onError(exception -> closed.set(true));

        observerExecutor.execute(() -> stream(
                emitter,
                closed,
                initial,
                lastEventId
        ));
        return emitter;
    }

    private void stream(
            SseEmitter emitter,
            AtomicBoolean closed,
            InterviewEventObservation initial,
            String lastEventId
    ) {
        ActorId ownerId = initial.session().ownerId();
        UUID interviewId = initial.session().interviewId();
        String cursor = lastEventId;
        long deliveredMysqlVersion = -1;
        Instant nextHeartbeat = clock.instant().plus(HEARTBEAT_INTERVAL);
        RedisInfrastructureErrorType previousRedisError = null;
        InterviewEventObservation observation = initial;

        try {
            while (!closed.get() && !Thread.currentThread().isInterrupted()) {
                previousRedisError = logRedisState(
                        interviewId,
                        previousRedisError,
                        observation.redisErrorType()
                );

                for (StoredInterviewEvent event : observation.events()) {
                    sendStoredEvent(emitter, event);
                    cursor = event.eventId();
                }

                MockInterviewSession session = observation.session();
                if (session.version() != deliveredMysqlVersion) {
                    sendMysqlSnapshot(emitter, session);
                    deliveredMysqlVersion = session.version();
                }

                if (session.isTerminal()) {
                    emitter.complete();
                    return;
                }

                if (observation.events().size() < BATCH_SIZE) {
                    Instant now = clock.instant();
                    if (!now.isBefore(nextHeartbeat)) {
                        sendHeartbeat(emitter, interviewId, now);
                        nextHeartbeat = now.plus(HEARTBEAT_INTERVAL);
                    }
                    Thread.sleep(POLL_INTERVAL.toMillis());
                }

                observation = queryService.observeForActor(
                        ownerId,
                        interviewId,
                        cursor,
                        BATCH_SIZE
                );
            }
            emitter.complete();
        } catch (IOException exception) {
            log.debug("面试SSE连接已经断开，interviewId={}", interviewId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (RuntimeException exception) {
            log.warn("面试SSE观察终止，interviewId={}, errorType={}",
                    interviewId, exception.getClass().getSimpleName());
            emitter.complete();
        } finally {
            closed.set(true);
        }
    }

    private RedisInfrastructureErrorType logRedisState(
            UUID interviewId,
            RedisInfrastructureErrorType previous,
            RedisInfrastructureErrorType current
    ) {
        if (current != null && current != previous) {
            log.warn("面试SSE暂时无法读取Redis事件，interviewId={}, errorType={}",
                    interviewId, current);
        } else if (current == null && previous != null) {
            log.info("面试SSE已恢复读取Redis事件，interviewId={}", interviewId);
        }
        return current;
    }

    private void sendStoredEvent(
            SseEmitter emitter,
            StoredInterviewEvent event
    ) throws IOException {
        emitter.send(
                SseEmitter.event()
                        .id(event.eventId())
                        .name(event.type().name())
                        .data(InterviewSseEventResponse.fromRedis(event))
        );
    }

    private void sendMysqlSnapshot(
            SseEmitter emitter,
            MockInterviewSession session
    ) throws IOException {
        emitter.send(
                SseEmitter.event()
                        .name("STATE_SNAPSHOT")
                        .data(InterviewSseEventResponse.fromMysql(session))
        );
    }

    private void sendHeartbeat(
            SseEmitter emitter,
            UUID interviewId,
            Instant sentAt
    ) throws IOException {
        emitter.send(
                SseEmitter.event()
                        .name("HEARTBEAT")
                        .data(new InterviewHeartbeatResponse(interviewId, sentAt))
        );
    }
}