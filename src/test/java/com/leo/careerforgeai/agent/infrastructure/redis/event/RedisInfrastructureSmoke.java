package com.leo.careerforgeai.agent.infrastructure.redis.event;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunEventStore;
import com.leo.careerforgeai.agent.application.run.event.CoachingRunEvent;
import com.leo.careerforgeai.agent.application.run.event.StoredCoachingRunEvent;
import com.leo.careerforgeai.agent.domain.run.CoachingRunStatus;
import com.leo.careerforgeai.agent.infrastructure.redis.health.RedisAvailabilityProbe;
import com.leo.careerforgeai.agent.infrastructure.redis.key.CareerForgeRedisKeyFactory;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 使用真实Redis验证PING、TTL、严格裁剪、续读和Redis事件丢失后MySQL事实不变
 * @author: Miao Zheng
 * @date: 2026-08-21
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "careerforge.persistence.enabled=true",
        "careerforge.model.base-url=http://localhost",
        "careerforge.model.api-key=redis-smoke-placeholder",
        "careerforge.model.name=redis-smoke-model",
        "spring.ai.mcp.server.enabled=false",
        "careerforge.redis.environment=smoke",
        "careerforge.redis.event-ttl=30s",
        "careerforge.redis.event-stream-max-length=5"
})
class RedisInfrastructureSmoke {

    private static final ActorId OWNER_ID = new ActorId("redis-smoke-owner");
    private static final Instant BASE_TIME = Instant.parse("2026-08-21T00:00:00Z");

    private final RedisAvailabilityProbe availabilityProbe;
    private final CoachingRunEventStore eventStore;
    private final CareerForgeRedisKeyFactory keyFactory;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    RedisInfrastructureSmoke(
            RedisAvailabilityProbe availabilityProbe,
            CoachingRunEventStore eventStore,
            CareerForgeRedisKeyFactory keyFactory,
            StringRedisTemplate redisTemplate,
            JdbcTemplate jdbcTemplate
    ) {
        this.availabilityProbe = availabilityProbe;
        this.eventStore = eventStore;
        this.keyFactory = keyFactory;
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void shouldVerifyTtlTrimmingReplayAndMysqlIndependence() {
        UUID runId = UUID.randomUUID();
        String streamKey = keyFactory.runEventStreamKey(OWNER_ID, runId);
        Map<String, Long> mysqlFactsBefore = readMysqlFacts();

        try {
            availabilityProbe.verifyAvailable();

            for (int index = 0; index < 7; index++) {
                eventStore.append(CoachingRunEvent.runState(
                        OWNER_ID,
                        runId,
                        CoachingRunStatus.RUNNING,
                        BASE_TIME.plusMillis(index)
                ));
            }

            Long streamSize = redisTemplate.opsForStream().size(streamKey);
            Long ttlSeconds = redisTemplate.getExpire(streamKey, TimeUnit.SECONDS);
            List<StoredCoachingRunEvent> retainedEvents = eventStore.readAfter(OWNER_ID, runId, null, 10);

            assertThat(streamSize).isEqualTo(5L);
            assertThat(ttlSeconds).isBetween(1L, 30L);
            assertThat(retainedEvents).hasSize(5);
            assertThat(retainedEvents)
                    .extracting(StoredCoachingRunEvent::occurredAt)
                    .containsExactly(
                            BASE_TIME.plusMillis(2),
                            BASE_TIME.plusMillis(3),
                            BASE_TIME.plusMillis(4),
                            BASE_TIME.plusMillis(5),
                            BASE_TIME.plusMillis(6)
                    );

            String firstRetainedEventId = retainedEvents.getFirst().eventId();
            assertThat(eventStore.readAfter(OWNER_ID, runId, firstRetainedEventId, 10))
                    .containsExactlyElementsOf(retainedEvents.subList(1, retainedEvents.size()));

            assertThat(redisTemplate.delete(streamKey)).isTrue();
            assertThat(redisTemplate.hasKey(streamKey)).isFalse();
            assertThat(eventStore.readAfter(OWNER_ID, runId, null, 10)).isEmpty();
            assertThat(readMysqlFacts()).isEqualTo(mysqlFactsBefore);
        } finally {
            redisTemplate.delete(streamKey);
        }
    }

    private Map<String, Long> readMysqlFacts() {
        Long runCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coaching_run", Long.class);
        Long turnCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coaching_turn", Long.class);
        return Map.of("runCount", runCount, "turnCount", turnCount);
    }
}