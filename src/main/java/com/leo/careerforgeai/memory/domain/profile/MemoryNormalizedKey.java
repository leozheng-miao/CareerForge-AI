package com.leo.careerforgeai.memory.domain.profile;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存按Memory类型确定性生成的冲突槽位或技能分组键
 * @author: Miao Zheng
 * @date: 2026-08-12
 * @param value 标准化后的槽位或技能分组值，例如primary、weekly_hours或spring boot
 * @param normalizationVersion 生成该标准值时使用的确定性规则版本
 **/
public record MemoryNormalizedKey(
        String value,
        String normalizationVersion
) {

    public static final String CAREER_GOAL_VERSION = "career-goal-v1";
    public static final String PREFERENCE_VERSION = "preference-v1";
    public static final String TIME_CONSTRAINT_VERSION = "time-constraint-v1";
    public static final String SKILL_VERSION = "skill-v1";

    private static final int MAX_VALUE_LENGTH = 128;
    private static final int MAX_VERSION_LENGTH = 32;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final Map<String, String> SKILL_ALIASES = Map.of(
            "springboot", "spring boot",
            "spring-boot", "spring boot",
            "mybatis plus", "mybatis-plus",
            "mybatisplus", "mybatis-plus"
    );

    public MemoryNormalizedKey {
        value = normalizeRequired(value, "value", MAX_VALUE_LENGTH);
        normalizationVersion = normalizeRequired(
                normalizationVersion,
                "normalizationVersion",
                MAX_VERSION_LENGTH
        );
    }

    public static MemoryNormalizedKey careerGoal() {
        return new MemoryNormalizedKey(
                "primary",
                CAREER_GOAL_VERSION
        );
    }

    public static MemoryNormalizedKey learningPreference(
            LearningPreferenceKey preferenceKey
    ) {
        Objects.requireNonNull(
                preferenceKey,
                "preferenceKey 不能为空"
        );

        return new MemoryNormalizedKey(
                preferenceKey.value(),
                PREFERENCE_VERSION
        );
    }

    public static MemoryNormalizedKey timeConstraint(
            TimeConstraintKey constraintKey
    ) {
        Objects.requireNonNull(
                constraintKey,
                "constraintKey 不能为空"
        );

        return new MemoryNormalizedKey(
                constraintKey.value(),
                TIME_CONSTRAINT_VERSION
        );
    }

    public static MemoryNormalizedKey skillEvidence(String rawSkillName) {
        String normalizedSkill = normalizeRequired(
                rawSkillName,
                "rawSkillName",
                MAX_VALUE_LENGTH
        ).toLowerCase(Locale.ROOT);

        normalizedSkill = WHITESPACE_PATTERN
                .matcher(normalizedSkill)
                .replaceAll(" ");

        normalizedSkill = SKILL_ALIASES.getOrDefault(
                normalizedSkill,
                normalizedSkill
        );

        return new MemoryNormalizedKey(
                normalizedSkill,
                SKILL_VERSION
        );
    }

    public boolean supports(MemoryType memoryType) {
        Objects.requireNonNull(memoryType, "memoryType 不能为空");

        return switch (memoryType) {
            case CAREER_GOAL ->
                    value.equals("primary")
                            && normalizationVersion.equals(CAREER_GOAL_VERSION);
            case LEARNING_PREFERENCE ->
                    LearningPreferenceKey.supports(value)
                            && normalizationVersion.equals(PREFERENCE_VERSION);
            case TIME_CONSTRAINT ->
                    TimeConstraintKey.supports(value)
                            && normalizationVersion.equals(TIME_CONSTRAINT_VERSION);
            case SKILL_EVIDENCE ->
                    normalizationVersion.equals(SKILL_VERSION);
        };
    }

    private static String normalizeRequired(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }

        String normalized = value.strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " 超过长度限制");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    fieldName + " 不能包含控制字符"
            );
        }

        return normalized;
    }
}