package com.leo.careerforgeai.interviewflow.infrastructure.redis;

import com.leo.careerforgeai.agent.infrastructure.redis.key.CareerForgeRedisKeyFactory;
import com.leo.careerforgeai.interviewflow.application.port.InterviewActivityLease;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 使用安全键和TTL实现可丢失的面试页面活动租约
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Component
public class RedisInterviewActivityLease implements InterviewActivityLease {

    private static final String ACTIVE_VALUE = "active";

    private final StringRedisTemplate redisTemplate;
    private final CareerForgeRedisKeyFactory keyFactory;

    public RedisInterviewActivityLease(
            StringRedisTemplate redisTemplate,
            CareerForgeRedisKeyFactory keyFactory
    ) {
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate,
                "redisTemplate不能为null"
        );
        this.keyFactory = Objects.requireNonNull(
                keyFactory,
                "keyFactory不能为null"
        );
    }

    @Override
    public void refresh(
            ActorId ownerId,
            UUID interviewId,
            Duration leaseDuration
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为null");
        Objects.requireNonNull(interviewId, "interviewId不能为null");
        Objects.requireNonNull(leaseDuration, "leaseDuration不能为null");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration必须大于0");
        }
        redisTemplate.opsForValue().set(
                key(ownerId, interviewId),
                ACTIVE_VALUE,
                leaseDuration
        );
    }

    @Override
    public boolean isActive(ActorId ownerId, UUID interviewId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(
                key(ownerId, interviewId)
        ));
    }

    @Override
    public void revoke(ActorId ownerId, UUID interviewId) {
        redisTemplate.delete(key(ownerId, interviewId));
    }

    private String key(ActorId ownerId, UUID interviewId) {
        return keyFactory.interviewActivityLeaseKey(ownerId, interviewId);
    }
}
