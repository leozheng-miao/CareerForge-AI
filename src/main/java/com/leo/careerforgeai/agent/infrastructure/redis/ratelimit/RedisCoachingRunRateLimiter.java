package com.leo.careerforgeai.agent.infrastructure.redis.ratelimit;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRateLimiter;
import com.leo.careerforgeai.agent.application.run.ratelimit.CoachingRunRateLimitDecision;
import com.leo.careerforgeai.agent.config.CoachingRunRateLimitProperties;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureErrorType;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import com.leo.careerforgeai.agent.infrastructure.redis.key.CareerForgeRedisKeyFactory;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 使用单Key Lua脚本原子执行owner维度Coaching Run固定窗口限流
 * @author: Miao Zheng
 * @date: 2026-08-23
 */
@Component
public class RedisCoachingRunRateLimiter implements CoachingRunRateLimiter {

    private static final String OPERATION = "coaching-run-submit";

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            """
            local limit = tonumber(ARGV[1])
            local windowMillis = tonumber(ARGV[2])
            local current = redis.call('GET', KEYS[1])

            if not current then
                redis.call('SET', KEYS[1], 1, 'PX', windowMillis)
                return {1, limit - 1, windowMillis}
            end

            current = tonumber(current)
            local ttlMillis = redis.call('PTTL', KEYS[1])

            if ttlMillis <= 0 then
                redis.call('PEXPIRE', KEYS[1], windowMillis)
                ttlMillis = windowMillis
            end

            if current >= limit then
                return {0, 0, ttlMillis}
            end

            current = redis.call('INCR', KEYS[1])
            return {1, limit - current, ttlMillis}
            """,
            List.class
    );

    private final StringRedisTemplate redisTemplate;
    private final CareerForgeRedisKeyFactory keyFactory;
    private final CoachingRunRateLimitProperties properties;

    public RedisCoachingRunRateLimiter(
            StringRedisTemplate redisTemplate,
            CareerForgeRedisKeyFactory keyFactory,
            CoachingRunRateLimitProperties properties
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate不能为空");
        this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory不能为空");
        this.properties = Objects.requireNonNull(properties, "properties不能为空");
    }

    @Override
    public CoachingRunRateLimitDecision acquire(ActorId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        String key = keyFactory.ownerRateLimitKey(ownerId, OPERATION);

        try {
            List<?> result = redisTemplate.execute(
                    ACQUIRE_SCRIPT,
                    List.of(key),
                    Integer.toString(properties.maxRequests()),
                    Long.toString(properties.window().toMillis())
            );
            return restoreDecision(result);
        } catch (QueryTimeoutException exception) {
            throw infrastructureFailure(RedisInfrastructureErrorType.TIMED_OUT, "Redis Run限流执行超时", exception);
        } catch (RedisConnectionFailureException exception) {
            throw infrastructureFailure(RedisInfrastructureErrorType.UNAVAILABLE, "Redis Run限流不可用", exception);
        } catch (DataAccessException exception) {
            throw infrastructureFailure(RedisInfrastructureErrorType.COMMAND_FAILED, "Redis Run限流执行失败", exception);
        }
    }

    private static CoachingRunRateLimitDecision restoreDecision(List<?> result) {
        if (result == null || result.size() != 3) {
            throw new RedisInfrastructureException(
                    RedisInfrastructureErrorType.UNEXPECTED_RESPONSE,
                    "Redis Run限流返回结构不合法"
            );
        }

        long allowedValue = toLong(result.get(0), "allowed");
        long remaining = toLong(result.get(1), "remaining");
        long ttlMillis = toLong(result.get(2), "ttlMillis");

        if ((allowedValue != 0 && allowedValue != 1) || remaining < 0 || ttlMillis <= 0) {
            throw new RedisInfrastructureException(
                    RedisInfrastructureErrorType.UNEXPECTED_RESPONSE,
                    "Redis Run限流返回值不合法"
            );
        }

        return new CoachingRunRateLimitDecision(
                allowedValue == 1,
                remaining,
                Duration.ofMillis(ttlMillis)
        );
    }

    private static long toLong(Object value, String field) {
        try {
            if (value instanceof Number number) return number.longValue();
            if (value instanceof String text) return Long.parseLong(text);
            if (value instanceof byte[] bytes) {
                return Long.parseLong(new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (NumberFormatException exception) {
            throw unexpectedField(field, exception);
        }
        throw unexpectedField(field, null);
    }

    private static RedisInfrastructureException unexpectedField(String field, Throwable cause) {
        return new RedisInfrastructureException(
                RedisInfrastructureErrorType.UNEXPECTED_RESPONSE,
                "Redis Run限流字段不合法: " + field,
                cause
        );
    }

    private static RedisInfrastructureException infrastructureFailure(
            RedisInfrastructureErrorType errorType,
            String message,
            Throwable cause
    ) {
        return new RedisInfrastructureException(errorType, message, cause);
    }
}