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
import com.leo.careerforgeai.agent.application.port.run.CoachingRunRateLimiter;
import com.leo.careerforgeai.agent.application.run.ratelimit.CoachingRunRateLimitDecision;
import com.leo.careerforgeai.agent.config.CoachingRunRateLimitProperties;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        "careerforge.redis.event-stream-max-length=5",
        "careerforge.agent.run-rate-limit.max-requests=3",
        "careerforge.agent.run-rate-limit.window=10s"
})
class RedisInfrastructureSmoke {

    private static final ActorId OWNER_ID = new ActorId("redis-smoke-owner");
    private static final Instant BASE_TIME = Instant.parse("2026-08-21T00:00:00Z");
    private static final String RATE_LIMIT_OPERATION = "coaching-run-submit";

    private final RedisAvailabilityProbe availabilityProbe;
    private final CoachingRunEventStore eventStore;
    private final CareerForgeRedisKeyFactory keyFactory;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final CoachingRunRateLimiter rateLimiter;
    private final CoachingRunRateLimitProperties rateLimitProperties;

    @Autowired
    RedisInfrastructureSmoke(
            RedisAvailabilityProbe availabilityProbe,
            CoachingRunEventStore eventStore,
            CoachingRunRateLimiter rateLimiter,
            CoachingRunRateLimitProperties rateLimitProperties,
            CareerForgeRedisKeyFactory keyFactory,
            StringRedisTemplate redisTemplate,
            JdbcTemplate jdbcTemplate
    ) {
        this.availabilityProbe = availabilityProbe;
        this.eventStore = eventStore;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
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

    @Test
    void shouldAtomicallyLimitConcurrentRequestsAndKeepTtl() throws Exception {
        ActorId ownerId = new ActorId("redis-rate-smoke-" + UUID.randomUUID());
        String rateLimitKey = keyFactory.ownerRateLimitKey(
                ownerId,
                RATE_LIMIT_OPERATION
        );
        int concurrentRequests = 20;
        CountDownLatch start = new CountDownLatch(1);

        redisTemplate.delete(rateLimitKey);

        try {
            availabilityProbe.verifyAvailable();
            List<Future<CoachingRunRateLimitDecision>> futures =
                    new ArrayList<>(concurrentRequests);

            try (ExecutorService executor =
                         Executors.newVirtualThreadPerTaskExecutor()) {
                for (int index = 0; index < concurrentRequests; index++) {
                    futures.add(executor.submit(() -> {
                        try {
                            start.await();
                            return rateLimiter.acquire(ownerId);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(
                                    "Redis限流Smoke被中断",
                                    exception
                            );
                        }
                    }));
                }

                start.countDown();

                List<CoachingRunRateLimitDecision> decisions =
                        new ArrayList<>(concurrentRequests);
                for (Future<CoachingRunRateLimitDecision> future : futures) {
                    decisions.add(future.get(5, TimeUnit.SECONDS));
                }

                List<CoachingRunRateLimitDecision> allowed = decisions.stream()
                        .filter(CoachingRunRateLimitDecision::allowed)
                        .toList();
                List<CoachingRunRateLimitDecision> rejected = decisions.stream()
                        .filter(decision -> !decision.allowed())
                        .toList();

                assertThat(allowed)
                        .hasSize(rateLimitProperties.maxRequests());
                assertThat(allowed)
                        .extracting(CoachingRunRateLimitDecision::remaining)
                        .containsExactlyInAnyOrder(0L, 1L, 2L);
                assertThat(rejected)
                        .hasSize(
                                concurrentRequests
                                        - rateLimitProperties.maxRequests()
                        );
                assertThat(rejected)
                        .allSatisfy(decision -> {
                            assertThat(decision.remaining()).isZero();
                            assertThat(decision.resetAfter()).isPositive();
                        });
            }

            assertThat(redisTemplate.opsForValue().get(rateLimitKey))
                    .isEqualTo(
                            Integer.toString(
                                    rateLimitProperties.maxRequests()
                            )
                    );

            Long ttlSeconds = redisTemplate.getExpire(
                    rateLimitKey,
                    TimeUnit.SECONDS
            );
            assertThat(ttlSeconds).isBetween(1L, 10L);
        } finally {
            redisTemplate.delete(rateLimitKey);
        }
    }

    private Map<String, Long> readMysqlFacts() {
        Long runCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coaching_run", Long.class);
        Long turnCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coaching_turn", Long.class);
        return Map.of("runCount", runCount, "turnCount", turnCount);
    }
}