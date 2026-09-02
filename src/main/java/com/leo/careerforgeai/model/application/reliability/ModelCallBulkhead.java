package com.leo.careerforgeai.model.application.reliability;

import com.leo.careerforgeai.model.config.ModelCallBulkheadProperties;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * @program: CareerForge-AI
 * @description: 按供应商隔离单JVM模型在途调用容量并执行零等待舱壁控制。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
@Component
public class ModelCallBulkhead {

    private static final String LEGACY_PROVIDER_ID = "deepseek";
    private final int maxConcurrentCalls;
    private final ConcurrentMap<String, Semaphore> providerPermits = new ConcurrentHashMap<>();

    public ModelCallBulkhead(ModelCallBulkheadProperties properties) {
        Objects.requireNonNull(properties, "properties不能为空");
        this.maxConcurrentCalls = properties.maxConcurrentCalls();
    }

    public <T> T execute(Supplier<T> action) {
        return execute(LEGACY_PROVIDER_ID, action);
    }

    public <T> T execute(String providerId, Supplier<T> action) {
        Objects.requireNonNull(action, "action不能为空");
        String normalizedProviderId = requireProviderId(providerId);
        Semaphore permits = providerPermits.computeIfAbsent(normalizedProviderId,
                ignored -> new Semaphore(maxConcurrentCalls, true));
        if (!permits.tryAcquire()) {
            throw new ModelException(ModelErrorType.CAPACITY_REJECTED,
                    "模型供应商并发容量已满，provider=" + normalizedProviderId);
        }
        try {
            return action.get();
        } finally {
            permits.release();
        }
    }

    private static String requireProviderId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId不能为空");
        }
        String normalized = providerId.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 64
                || !normalized.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException("providerId格式非法");
        }
        return normalized;
    }
}