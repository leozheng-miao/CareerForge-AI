// ModelExecutionProfile.java
package com.leo.careerforgeai.model.domain.routing;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 保存一个可由路由器选择的不可变模型执行配置。
 * @author: Miao Zheng
 * @date: 2026-09-02
 * @param profileId 稳定逻辑配置ID
 * @param provider 供应商ID
 * @param model 供应商模型名
 * @param capabilities 已验证能力
 * @param reasoningMode 推理模式
 * @param reasoningEffort 推理强度，关闭Thinking时必须为空
 * @param maxOutputTokens 最大输出Token
 * @param timeout 单次调用Deadline
 * @param priceVersion 价格配置版本
 * @param enabled 是否允许进入正式路由
 */
public record ModelExecutionProfile(
        String profileId,
        String provider,
        String model,
        Set<ModelCapability> capabilities,
        ReasoningMode reasoningMode,
        ReasoningEffort reasoningEffort,
        int maxOutputTokens,
        Duration timeout,
        String priceVersion,
        boolean enabled
) {

    public ModelExecutionProfile {
        profileId = requireId(profileId, "profileId");
        provider = requireId(provider, "provider");
        model = requireText(model, "model", 128);
        priceVersion = requireText(priceVersion, "priceVersion", 64);
        if (capabilities == null || capabilities.isEmpty()
                || capabilities.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("capabilities不能为空");
        }
        capabilities = Set.copyOf(capabilities);
        if (reasoningMode == null) throw new IllegalArgumentException("reasoningMode不能为空");
        if (reasoningMode == ReasoningMode.DISABLED && reasoningEffort != null) {
            throw new IllegalArgumentException("关闭Thinking时不能设置reasoningEffort");
        }
        if (reasoningMode != ReasoningMode.DISABLED && reasoningEffort == null) {
            throw new IllegalArgumentException("开启或自适应Thinking时必须设置reasoningEffort");
        }
        if (reasoningMode != ReasoningMode.DISABLED && !capabilities.contains(ModelCapability.THINKING)) {
            throw new IllegalArgumentException("当前Profile不支持Thinking");
        }
        if (maxOutputTokens <= 0) throw new IllegalArgumentException("maxOutputTokens必须大于0");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout必须大于0");
        }
    }

    public boolean supports(Set<ModelCapability> requiredCapabilities) {
        return requiredCapabilities != null
                && requiredCapabilities.stream().noneMatch(java.util.Objects::isNull)
                && capabilities.containsAll(requiredCapabilities);
    }

    private static String requireId(String value, String field) {
        String normalized = requireText(value, field, 64).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException(field + "格式非法");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
        String normalized = value.strip();
        if (normalized.length() > maxLength || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + "格式非法");
        }
        return normalized;
    }
}