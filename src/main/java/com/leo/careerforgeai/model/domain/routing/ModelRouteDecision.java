// ModelRouteDecision.java
package com.leo.careerforgeai.model.domain.routing;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 保存一次不可由用户覆盖的模型路由决策和允许的降级顺序。
 * @author: Miao Zheng
 * @date: 2026-09-02
 * @param taskType 业务任务类型
 * @param selectedProfile 首选执行Profile
 * @param fallbackProfiles 允许的有序Fallback列表
 * @param routingVersion 路由策略版本
 * @param reasonCode 稳定选择原因
 */
public record ModelRouteDecision(
        ModelTaskType taskType,
        ModelExecutionProfile selectedProfile,
        List<ModelExecutionProfile> fallbackProfiles,
        String routingVersion,
        String reasonCode
) {

    public ModelRouteDecision {
        if (taskType == null) throw new IllegalArgumentException("taskType不能为空");
        if (selectedProfile == null || !selectedProfile.enabled()) {
            throw new IllegalArgumentException("selectedProfile必须启用");
        }
        if (fallbackProfiles == null
                || fallbackProfiles.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("fallbackProfiles不能为空且不能包含null");
        }
        fallbackProfiles = List.copyOf(fallbackProfiles);
        routingVersion = requireText(routingVersion, "routingVersion");
        reasonCode = requireText(reasonCode, "reasonCode");

        Set<String> profileIds = new HashSet<>();
        profileIds.add(selectedProfile.profileId());
        for (ModelExecutionProfile fallback : fallbackProfiles) {
            if (!fallback.enabled()) throw new IllegalArgumentException("Fallback Profile必须启用");
            if (!profileIds.add(fallback.profileId())) {
                throw new IllegalArgumentException("路由决策不能包含重复Profile");
            }
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
        String normalized = value.strip();
        if (normalized.length() > 128 || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + "格式非法");
        }
        return normalized;
    }
}