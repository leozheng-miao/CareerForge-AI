package com.leo.careerforgeai.interviewflow.application.resume;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public final class ProfileDraftValidator {
    private static final int MAX_SUMMARY = 400;
    private static final int MAX_DESCRIPTION = 240;
    private static final int MAX_ITEM = 160;
    private static final int MAX_SKILLS = 24;
    private static final int MAX_TECHNOLOGIES = 16;
    private static final int MAX_EXPERIENCES_PER_GROUP = 8;
    private static final int MAX_EXPERIENCES_TOTAL = 12;

    public ProfileDraft validate(ProfileDraft draft) {
        Objects.requireNonNull(draft, "draft不能为空");
        ProfileDraft normalized = normalize(draft);
        requireLength(normalized.personalSummary(), MAX_SUMMARY, "personalSummary");
        requireList(normalized.coreSkills(), "coreSkills", MAX_SKILLS);
        validateExperiences(normalized.projectExperiences(), "projectExperiences");
        validateExperiences(normalized.workExperiences(), "workExperiences");
        validateExperiences(normalized.internshipExperiences(), "internshipExperiences");
        int totalExperiences = normalized.projectExperiences().size()
                + normalized.workExperiences().size() + normalized.internshipExperiences().size();
        if (totalExperiences > MAX_EXPERIENCES_TOTAL) {
            throw new IllegalArgumentException("经历总数超过限制");
        }
        return normalized;
    }

    private static ProfileDraft normalize(ProfileDraft draft) {
        return new ProfileDraft(
                compact(draft.personalSummary(), MAX_SUMMARY),
                compactStrings(draft.coreSkills(), MAX_ITEM),
                compactExperiences(draft.projectExperiences()),
                compactExperiences(draft.workExperiences()),
                compactExperiences(draft.internshipExperiences())
        );
    }

    private static List<String> compactStrings(List<String> values, int maxLength) {
        Objects.requireNonNull(values, "values不能为null");
        return values.stream()
                .filter(Objects::nonNull)
                .map(value -> compact(value, maxLength))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static List<ProfileExperienceDraft> compactExperiences(
            List<ProfileExperienceDraft> values) {
        Objects.requireNonNull(values, "values不能为null");
        return values.stream()
                .filter(Objects::nonNull)
                .map(value -> new ProfileExperienceDraft(
                        compact(value.name(), MAX_ITEM),
                        compact(value.role(), MAX_ITEM),
                        compact(value.period(), MAX_ITEM),
                        compact(value.description(), MAX_DESCRIPTION),
                        compactStrings(value.technologies(), MAX_ITEM)))
                .toList();
    }

    private static String compact(String value, int maxLength) {
        Objects.requireNonNull(value, "value不能为null");
        String normalized = value.strip().replaceAll("[\\p{Z}\\s]+", " ");
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= maxLength) return normalized;
        int end = normalized.offsetByCodePoints(0, maxLength);
        return normalized.substring(0, end).stripTrailing();
    }

    private static void validateExperiences(List<ProfileExperienceDraft> values, String field) {
        requireSize(values, MAX_EXPERIENCES_PER_GROUP, field);
        for (ProfileExperienceDraft value : values) {
            requireLength(value.name(), MAX_ITEM, field + ".name");
            requireLength(value.role(), MAX_ITEM, field + ".role");
            requireLength(value.period(), MAX_ITEM, field + ".period");
            requireLength(value.description(), MAX_DESCRIPTION, field + ".description");
            requireList(value.technologies(), field + ".technologies", MAX_TECHNOLOGIES);
        }
    }

    private static void requireList(List<String> values, String field, int maxSize) {
        requireSize(values, maxSize, field);
        values.forEach(value -> requireLength(value, MAX_ITEM, field));
    }

    private static void requireSize(List<?> values, int maxSize, String field) {
        Objects.requireNonNull(values, field + "不能为null");
        if (values.size() > maxSize) throw new IllegalArgumentException(field + "条目过多");
    }

    private static void requireLength(String value, int max, String field) {
        Objects.requireNonNull(value, field + "不能为null");
        if (value.length() > max || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + "长度或字符不合法");
        }
    }
}
