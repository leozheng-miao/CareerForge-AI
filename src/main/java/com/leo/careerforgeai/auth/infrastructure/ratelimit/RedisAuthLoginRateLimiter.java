package com.leo.careerforgeai.auth.infrastructure.ratelimit;

import com.leo.careerforgeai.auth.application.port.AuthLoginRateLimiter;
import com.leo.careerforgeai.auth.config.AuthLoginRateLimitProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 使用Redis原子固定窗口限制登录尝试且Key不暴露邮箱和来源地址
 * @author: Miao Zheng
 * @date: 2026-09-03
 **/
@Component
@ConditionalOnProperty(prefix = "careerforge.auth", name = "enabled", havingValue = "true")
public class RedisAuthLoginRateLimiter implements AuthLoginRateLimiter {

    private static final Pattern KEY_TOKEN = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            if count > tonumber(ARGV[1]) then
                return 0
            end
            return 1
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final AuthLoginRateLimitProperties properties;
    private final String keyPrefix;

    public RedisAuthLoginRateLimiter(
            StringRedisTemplate redisTemplate,
            AuthLoginRateLimitProperties properties,
            @Value("${careerforge.redis.namespace}") String namespace,
            @Value("${careerforge.redis.environment}") String environment
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate不能为空");
        this.properties = Objects.requireNonNull(properties, "properties不能为空");
        this.keyPrefix = requireKeyToken(namespace, "namespace") + ":"
                + requireKeyToken(environment, "environment") + ":auth:login:";
    }

    @Override
    public boolean tryAcquire(String remoteAddress, String email) {
        String source = remoteAddress == null || remoteAddress.isBlank()
                ? "unknown" : remoteAddress.strip();
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email不能为空");
        String identityHash = sha256(source + "\n" + email.strip().toLowerCase(Locale.ROOT));
        Long allowed = redisTemplate.execute(
                ACQUIRE_SCRIPT,
                List.of(keyPrefix + "{" + identityHash + "}"),
                Integer.toString(properties.maxAttempts()),
                Long.toString(properties.window().toMillis())
        );
        if (allowed == null || (allowed != 0L && allowed != 1L)) {
            throw new DataAccessResourceFailureException("Redis登录限流返回异常");
        }
        return allowed == 1L;
    }

    private static String requireKeyToken(String value, String fieldName) {
        if (value == null || !KEY_TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + "格式不合法");
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JVM不支持SHA-256", exception);
        }
    }
}