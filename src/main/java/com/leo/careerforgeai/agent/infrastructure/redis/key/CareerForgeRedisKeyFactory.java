package com.leo.careerforgeai.agent.infrastructure.redis.key;

import com.leo.careerforgeai.agent.config.CareerForgeRedisProperties;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 生成环境隔离且不暴露原始ownerId的Redis Key
 * @author: Miao Zheng
 * @date: 2026-08-21
 */
@Component
public class CareerForgeRedisKeyFactory {

    private static final Pattern OPERATION_PATTERN = Pattern.compile("[a-z][a-z0-9-]{0,31}");

    private final CareerForgeRedisProperties properties;

    public CareerForgeRedisKeyFactory(CareerForgeRedisProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties不能为空");
    }

    public String runEventStreamKey(ActorId ownerId, UUID runId) {
        Objects.requireNonNull(runId, "runId不能为空");
        return ownerPrefix(ownerId) + ":run:" + runId + ":events";
    }

    public String interviewEventStreamKey(ActorId ownerId, UUID interviewId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        return ownerPrefix(ownerId) + ":interview:" + interviewId + ":events";
    }

    public String ownerRateLimitKey(ActorId ownerId, String operation) {
        requireOperation(operation);
        return ownerPrefix(ownerId) + ":rate:" + operation;
    }

    public String globalRateLimitKey(String operation) {
        requireOperation(operation);
        return basePrefix() + ":{global}:rate:" + operation;
    }

    private String ownerPrefix(ActorId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        return basePrefix() + ":{" + sha256(ownerId.value()) + "}";
    }

    private String basePrefix() {
        return properties.namespace() + ":" + properties.environment();
    }

    private static void requireOperation(String operation) {
        if (operation == null || !OPERATION_PATTERN.matcher(operation).matches()) {
            throw new IllegalArgumentException("operation必须匹配[a-z][a-z0-9-]{0,31}");
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JVM不支持SHA-256", exception);
        }
    }
}