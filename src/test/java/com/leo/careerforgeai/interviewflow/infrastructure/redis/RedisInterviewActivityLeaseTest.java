package com.leo.careerforgeai.interviewflow.infrastructure.redis;

import com.leo.careerforgeai.agent.infrastructure.redis.key.CareerForgeRedisKeyFactory;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证面试活动租约只使用带TTL的Redis安全键
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@ExtendWith(MockitoExtension.class)
class RedisInterviewActivityLeaseTest {

    private static final ActorId OWNER = new ActorId("quick-owner-a");
    private static final UUID INTERVIEW_ID =
            UUID.fromString("35000000-0000-0000-0000-000000000001");
    private static final String LEASE_KEY =
            "careerforge:local:{owner-hash}:interview:"
                    + INTERVIEW_ID + ":activity";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CareerForgeRedisKeyFactory keyFactory;

    private RedisInterviewActivityLease lease;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(keyFactory.interviewActivityLeaseKey(OWNER, INTERVIEW_ID))
                .thenReturn(LEASE_KEY);
        lease = new RedisInterviewActivityLease(redisTemplate, keyFactory);
    }

    @Test
    void shouldRefreshCheckAndRevokeTheLeaseUsingRedisTtl() {
        Duration ttl = Duration.ofSeconds(30);
        when(redisTemplate.hasKey(LEASE_KEY)).thenReturn(true);

        lease.refresh(OWNER, INTERVIEW_ID, ttl);

        assertThat(lease.isActive(OWNER, INTERVIEW_ID)).isTrue();
        lease.revoke(OWNER, INTERVIEW_ID);
        verify(valueOperations).set(LEASE_KEY, "active", ttl);
        verify(redisTemplate).delete(LEASE_KEY);
    }

    @Test
    void shouldSurfaceRedisFailureForTheApplicationFailSafePath() {
        Duration ttl = Duration.ofSeconds(30);
        var failure = new RedisConnectionFailureException("unavailable");
        org.mockito.Mockito.doThrow(failure)
                .when(valueOperations)
                .set(LEASE_KEY, "active", ttl);

        assertThatThrownBy(() -> lease.refresh(OWNER, INTERVIEW_ID, ttl))
                .isSameAs(failure);
    }
}
