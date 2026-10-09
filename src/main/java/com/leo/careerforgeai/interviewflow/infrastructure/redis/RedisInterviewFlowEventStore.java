package com.leo.careerforgeai.interviewflow.infrastructure.redis;

import com.leo.careerforgeai.interviewflow.application.port.InterviewEventPublisher;
import com.leo.careerforgeai.interviewflow.application.port.InterviewEventPublisher.SafeInterviewEvent;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** 有界Redis事件回放加单实例实时通知；所有业务终态仍由MySQL/REST收敛。 */
@Component
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public final class RedisInterviewFlowEventStore implements InterviewEventPublisher {
    private static final int MAX_EVENTS = 100;
    private static final Duration TTL = Duration.ofHours(6);
    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final Map<String, CopyOnWriteArrayList<Consumer<SafeInterviewEvent>>> listeners =
            new ConcurrentHashMap<>();

    public RedisInterviewFlowEventStore(StringRedisTemplate redis, JsonMapper json) {
        this.redis = Objects.requireNonNull(redis);
        this.json = Objects.requireNonNull(json);
    }

    @Override
    public void publish(SafeInterviewEvent event) {
        Objects.requireNonNull(event);
        String key = key(event.ownerId(), event.interviewId());
        try {
            redis.opsForList().rightPush(key, json.writeValueAsString(event));
            redis.opsForList().trim(key, -MAX_EVENTS, -1);
            redis.expire(key, TTL);
        } catch (RuntimeException ignored) {
            // SSE是辅助通道，Redis故障不得回滚MySQL业务事实。
        }
        listeners.getOrDefault(key, new CopyOnWriteArrayList<>())
                .forEach(listener -> {
                    try { listener.accept(event); }
                    catch (RuntimeException ignored) { }
                });
    }

    @Override
    public List<SafeInterviewEvent> replay(ActorId ownerId, UUID interviewId,
                                           String afterEventId, int limit) {
        if (limit < 1 || limit > MAX_EVENTS) {
            throw new IllegalArgumentException("limit不合法");
        }
        List<String> values;
        try {
            values = redis.opsForList().range(key(ownerId, interviewId), 0, -1);
        } catch (RuntimeException exception) {
            return List.of();
        }
        if (values == null) return List.of();
        boolean[] emit = {afterEventId == null || afterEventId.isBlank()};
        return values.stream().map(this::read).filter(Objects::nonNull)
                .filter(event -> {
                    if (emit[0]) return true;
                    if (event.eventId().equals(afterEventId)) emit[0] = true;
                    return false;
                }).limit(limit).toList();
    }

    @Override
    public AutoCloseable subscribe(ActorId ownerId, UUID interviewId,
                                   Consumer<SafeInterviewEvent> consumer) {
        String key = key(ownerId, interviewId);
        listeners.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>())
                .add(consumer);
        return () -> listeners.computeIfPresent(key, (ignored, existing) -> {
            existing.remove(consumer);
            return existing.isEmpty() ? null : existing;
        });
    }

    private SafeInterviewEvent read(String value) {
        try {
            return json.readValue(value, SafeInterviewEvent.class);
        } catch (JacksonException exception) {
            return null;
        }
    }

    private static String key(ActorId ownerId, UUID interviewId) {
        return "careerforge:interview-flow:events:" + ownerId.value() + ":" + interviewId;
    }
}
