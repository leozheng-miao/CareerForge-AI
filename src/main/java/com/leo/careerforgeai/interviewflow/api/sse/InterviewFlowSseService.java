package com.leo.careerforgeai.interviewflow.api.sse;

import com.leo.careerforgeai.interviewflow.application.port.InterviewEventPublisher;
import com.leo.careerforgeai.interviewflow.application.port.InterviewEventPublisher.SafeInterviewEvent;
import com.leo.careerforgeai.interviewflow.application.query.InterviewQueryService;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class InterviewFlowSseService {
    private static final long TIMEOUT_MILLIS = 120_000L;
    private final CurrentActorProvider actors;
    private final InterviewQueryService queries;
    private final InterviewEventPublisher events;
    private final Clock clock;

    public InterviewFlowSseService(CurrentActorProvider actors,
            InterviewQueryService queries, InterviewEventPublisher events, Clock clock) {
        this.actors = Objects.requireNonNull(actors);
        this.queries = Objects.requireNonNull(queries);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
    }

    public SseEmitter open(UUID interviewId, String lastEventId) {
        var aggregate = queries.get(interviewId);
        var owner = actors.currentActor();
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        AtomicReference<AutoCloseable> subscription = new AtomicReference<>();
        Runnable close = () -> close(subscription.getAndSet(null));
        emitter.onCompletion(close);
        emitter.onTimeout(() -> { close.run(); emitter.complete(); });
        emitter.onError(ignored -> close.run());
        try {
            emitter.send(SseEmitter.event().name("snapshot")
                    .data(new Snapshot(interviewId, aggregate.session().stage().name(),
                            aggregate.session().version(), clock.instant())));
            for (SafeInterviewEvent event : events.replay(owner, interviewId, lastEventId, 100)) {
                send(emitter, event);
            }
            subscription.set(events.subscribe(owner, interviewId, event -> {
                try { send(emitter, event); }
                catch (IOException exception) { close.run(); emitter.completeWithError(exception); }
            }));
        } catch (IOException exception) {
            close.run();
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private static void send(SseEmitter emitter, SafeInterviewEvent event) throws IOException {
        emitter.send(SseEmitter.event().id(event.eventId())
                .name(event.type().name().toLowerCase()).data(event));
    }
    private static void close(AutoCloseable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (Exception ignored) { }
    }
    public record Snapshot(UUID interviewId, String status, long version,
                           java.time.Instant eventTime) {}
}
