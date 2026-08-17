package com.leo.careerforgeai.career.application;

import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 从冻结JobRequirements中生成供能力差距计算使用的稳定要求引用
 * @author: Miao Zheng
 * @date: 2026-08-16
 */
public final class SkillGapRequirementCatalog {
    private SkillGapRequirementCatalog() {
    }

    public static Map<String, String> extract(JobRequirements requirements) {
        Objects.requireNonNull(requirements, "requirements不能为空");
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        append(result, "programmingLanguages", requirements.programmingLanguages());
        append(result, "backendAndInfrastructureRequirements", requirements.backendAndInfrastructureRequirements());
        append(result, "agentRequirements", requirements.agentRequirements());
        append(result, "ragRequirements", requirements.ragRequirements());
        append(result, "engineeringRequirements", requirements.engineeringRequirements());
        append(result, "bonusQualifications", requirements.bonusQualifications());
        if (result.isEmpty()) {
            throw new IllegalArgumentException("目标岗位没有可评估的技能要求");
        }
        if (result.size() > SkillGapSnapshot.MAX_ITEMS) {
            throw new IllegalArgumentException("可评估岗位要求数量超过限制");
        }
        return Collections.unmodifiableMap(result);
    }

    private static void append(Map<String, String> result, String fieldName, List<String> values) {
        Objects.requireNonNull(values, fieldName + "不能为空");
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(fieldName + "包含空要求");
            }
            String normalized = value.strip();
            if (normalized.length() > SkillGapSnapshot.GapItem.MAX_REQUIREMENT_TEXT_LENGTH) {
                throw new IllegalStateException(fieldName + "要求超过长度限制");
            }
            result.put(fieldName + "[" + index + "]", normalized);
        }
    }
}