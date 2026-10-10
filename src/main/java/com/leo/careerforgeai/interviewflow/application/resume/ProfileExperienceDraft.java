package com.leo.careerforgeai.interviewflow.application.resume;

import java.util.List;

public record ProfileExperienceDraft(
        String name,
        String role,
        String period,
        String description,
        List<String> technologies
) {
    public ProfileExperienceDraft {
        name = normalize(name);
        role = normalize(role);
        period = normalize(period);
        description = normalize(description);
        technologies = technologies == null ? List.of() : technologies.stream()
                .map(ProfileExperienceDraft::normalize)
                .filter(value -> !value.isBlank())
                .distinct().toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
