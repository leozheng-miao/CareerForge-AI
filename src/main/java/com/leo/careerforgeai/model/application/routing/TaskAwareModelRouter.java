package com.leo.careerforgeai.model.application.routing;

import com.leo.careerforgeai.model.domain.routing.ModelCapability;
import com.leo.careerforgeai.model.domain.routing.ModelExecutionProfile;
import com.leo.careerforgeai.model.domain.routing.ModelRouteDecision;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 按Java业务任务、能力、Token预算和Deadline选择模型执行Profile。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
public final class TaskAwareModelRouter {

    private final Map<ModelTaskType, List<ModelExecutionProfile>> routes;
    private final String routingVersion;

    public TaskAwareModelRouter(Map<ModelTaskType, List<ModelExecutionProfile>> routes, String routingVersion) {
        this.routes = copyRoutes(routes);
        if (routingVersion == null || routingVersion.isBlank()) throw new IllegalArgumentException("routingVersion不能为空");
        this.routingVersion = routingVersion.strip();
    }

    public ModelRouteDecision route(ModelTaskType taskType, Set<ModelCapability> requiredCapabilities,
                                    int requestedOutputTokens, int remainingTokenBudget,
                                    Duration remainingDeadline, boolean fallbackAllowed) {
        if (taskType == null) throw new IllegalArgumentException("taskType不能为空");
        Set<ModelCapability> required = validateConstraints(requiredCapabilities, requestedOutputTokens,
                remainingTokenBudget, remainingDeadline);
        List<ModelExecutionProfile> configured = routes.get(taskType);
        if (configured == null) throw unavailable(taskType, "未配置路由");

        List<ModelExecutionProfile> eligible = configured.stream()
                .filter(ModelExecutionProfile::enabled)
                .filter(profile -> profile.supports(required))
                .filter(profile -> profile.maxOutputTokens() >= requestedOutputTokens)
                .toList();
        if (eligible.isEmpty()) throw unavailable(taskType, "没有满足能力和Token约束的Profile");

        ModelExecutionProfile selected = eligible.get(0);
        List<ModelExecutionProfile> fallbacks = fallbackAllowed && eligible.size() > 1
                ? eligible.subList(1, eligible.size()) : List.of();
        String reasonCode = selected.equals(configured.get(0)) ? "TASK_DEFAULT" : "CONSTRAINT_FALLBACK";
        return new ModelRouteDecision(taskType, selected, fallbacks, routingVersion, reasonCode);
    }

    private static Set<ModelCapability> validateConstraints(Set<ModelCapability> capabilities,
                                                            int requestedOutputTokens,
                                                            int remainingTokenBudget,
                                                            Duration remainingDeadline) {
        if (capabilities == null || capabilities.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("requiredCapabilities不能为空且不能包含null");
        }
        if (requestedOutputTokens <= 0) throw new IllegalArgumentException("requestedOutputTokens必须大于0");
        if (remainingTokenBudget < requestedOutputTokens) throw new IllegalArgumentException("剩余Token预算不足");
        if (remainingDeadline == null || remainingDeadline.isZero() || remainingDeadline.isNegative()) {
            throw new IllegalArgumentException("remainingDeadline必须大于0");
        }
        return Set.copyOf(capabilities);
    }

    private static Map<ModelTaskType, List<ModelExecutionProfile>> copyRoutes(
            Map<ModelTaskType, List<ModelExecutionProfile>> source) {
        if (source == null || source.isEmpty()) throw new IllegalArgumentException("routes不能为空");
        Map<ModelTaskType, List<ModelExecutionProfile>> result = new EnumMap<>(ModelTaskType.class);
        source.forEach((taskType, profiles) -> {
            if (taskType == null || profiles == null || profiles.isEmpty()) {
                throw new IllegalArgumentException("任务路由及Profile不能为空");
            }
            List<ModelExecutionProfile> copied = List.copyOf(profiles);
            Set<String> profileIds = new HashSet<>();
            if (copied.stream().anyMatch(java.util.Objects::isNull)
                    || copied.stream().anyMatch(profile -> !profileIds.add(profile.profileId()))) {
                throw new IllegalArgumentException("任务路由不能包含null或重复Profile");
            }
            result.put(taskType, copied);
        });
        return Map.copyOf(result);
    }

    private static ModelException unavailable(ModelTaskType taskType, String reason) {
        return new ModelException(ModelErrorType.CONFIGURATION_ERROR,
                "模型路由不可用，taskType=" + taskType + "，reason=" + reason);
    }
}