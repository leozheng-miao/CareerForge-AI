package com.leo.careerforgeai.model.config;

import com.leo.careerforgeai.model.domain.routing.ModelCapability;
import com.leo.careerforgeai.model.domain.routing.ModelExecutionProfile;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import com.leo.careerforgeai.model.domain.routing.ReasoningEffort;
import com.leo.careerforgeai.model.domain.routing.ReasoningMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 绑定并校验供应商、执行Profile和后端任务路由配置。
 * @author: Miao Zheng
 * @date: 2026-09-02
 * @param version 路由策略版本
 * @param providers 供应商连接配置
 * @param profiles 模型执行Profile
 * @param routes 任务对应的有序Profile ID
 */
@ConfigurationProperties(prefix = "careerforge.model-routing", ignoreUnknownFields = false)
@Validated
public record ModelRoutingProperties(
        @NotBlank String version,
        @NotEmpty Map<@NotBlank String, @Valid Provider> providers,
        @NotEmpty Map<@NotBlank String, @Valid Profile> profiles,
        @NotEmpty Map<@NotNull ModelTaskType, List<@NotBlank String>> routes
) {

    public ModelRoutingProperties {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version不能为空");
        }
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("providers不能为空");
        }
        if (profiles == null || profiles.isEmpty()) {
            throw new IllegalArgumentException("profiles不能为空");
        }
        if (routes == null || routes.isEmpty()) {
            throw new IllegalArgumentException("routes不能为空");
        }

        version = version.strip();

        // Lambda统一读取这两个不会再被赋值的局部变量
        Map<String, Provider> providerConfigs = Map.copyOf(providers);
        Map<String, Profile> profileConfigs = Map.copyOf(profiles);

        providerConfigs.forEach((id, provider) -> {
            if (!requireId(id, "providerId").equals(id) || provider == null) {
                throw new IllegalArgumentException("供应商ID或配置非法");
            }
        });

        Map<String, ModelExecutionProfile> executions = new java.util.HashMap<>();
        profileConfigs.forEach((id, profile) -> {
            if (!requireId(id, "profileId").equals(id) || profile == null) {
                throw new IllegalArgumentException("Profile ID或配置非法");
            }

            // 这里必须使用providerConfigs，不再使用providers
            Provider provider = providerConfigs.get(profile.provider());
            if (provider == null) {
                throw new IllegalArgumentException("Profile引用了未配置供应商: " + id);
            }

            executions.put(
                    id,
                    profile.toExecutionProfile(id, provider.enabled())
            );
        });

        Map<ModelTaskType, List<String>> copiedRoutes =
                new EnumMap<>(ModelTaskType.class);

        routes.forEach((taskType, profileIds) -> {
            if (taskType == null || profileIds == null || profileIds.isEmpty()) {
                throw new IllegalArgumentException("任务路由不能为空");
            }

            List<String> copiedIds = profileIds.stream()
                    .map(id -> requireId(id, "routeProfileId"))
                    .toList();

            if (new HashSet<>(copiedIds).size() != copiedIds.size()) {
                throw new IllegalArgumentException(
                        "任务路由不能包含重复Profile: " + taskType
                );
            }

            if (copiedIds.stream().anyMatch(id -> !executions.containsKey(id))) {
                throw new IllegalArgumentException(
                        "任务路由引用了未知Profile: " + taskType
                );
            }

            if (copiedIds.stream()
                    .map(executions::get)
                    .noneMatch(ModelExecutionProfile::enabled)) {
                throw new IllegalArgumentException(
                        "任务路由没有启用的Profile: " + taskType
                );
            }

            copiedRoutes.put(taskType, copiedIds);
        });

        // 最终赋值给record的三个组件
        providers = providerConfigs;
        profiles = profileConfigs;
        routes = Map.copyOf(copiedRoutes);
    }

    public Map<ModelTaskType, List<ModelExecutionProfile>> executionRoutes() {
        Map<String, ModelExecutionProfile> executions = new java.util.HashMap<>();
        profiles.forEach((id, profile) -> executions.put(id,
                profile.toExecutionProfile(id, providers.get(profile.provider()).enabled())));
        Map<ModelTaskType, List<ModelExecutionProfile>> result = new EnumMap<>(ModelTaskType.class);
        routes.forEach((taskType, ids) -> result.put(taskType,
                ids.stream().map(executions::get).toList()));
        return Map.copyOf(result);
    }

    private static String requireId(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException(field + "格式非法");
        }
        return normalized;
    }

    /**
     * @param baseUrl 供应商API根地址
     * @param apiKey 供应商API Key
     * @param enabled 是否允许该供应商进入正式路由
     */
    public record Provider(@NotNull URI baseUrl, String apiKey, boolean enabled) {
        public Provider {
            if (baseUrl == null) throw new IllegalArgumentException("baseUrl不能为空");
            apiKey = apiKey == null ? "" : apiKey.strip();
            if (enabled && apiKey.isBlank()) throw new IllegalArgumentException("启用供应商时apiKey不能为空");
        }
    }

    /**
     * @param provider 供应商ID
     * @param model 供应商模型名
     * @param capabilities 已验证能力
     * @param reasoningMode 推理模式
     * @param reasoningEffort 推理强度
     * @param maxOutputTokens 最大输出Token
     * @param timeout 单次调用超时上限
     * @param priceVersion 价格配置版本
     * @param enabled 是否启用Profile
     */
    public record Profile(
            @NotBlank String provider,
            @NotBlank String model,
            @NotEmpty Set<ModelCapability> capabilities,
            @NotNull ReasoningMode reasoningMode,
            ReasoningEffort reasoningEffort,
            int maxOutputTokens,
            @NotNull Duration timeout,
            @NotBlank String priceVersion,
            boolean enabled
    ) {
        public Profile {
            provider = requireId(provider, "provider");
            if (capabilities == null || capabilities.isEmpty()) {
                throw new IllegalArgumentException("capabilities不能为空");
            }
            capabilities = Set.copyOf(capabilities);
        }

        private ModelExecutionProfile toExecutionProfile(String profileId, boolean providerEnabled) {
            return new ModelExecutionProfile(profileId, provider, model, capabilities, reasoningMode,
                    reasoningEffort, maxOutputTokens, timeout, priceVersion, enabled && providerEnabled);
        }
    }
}