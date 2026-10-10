package com.leo.careerforgeai.interviewflow.application.resume;

import java.util.List;

public record ProfileDraft(
        String personalSummary,
        List<String> coreSkills,
        List<ProfileExperienceDraft> projectExperiences,
        List<ProfileExperienceDraft> workExperiences,
        List<ProfileExperienceDraft> internshipExperiences
) {
    public ProfileDraft {
        personalSummary = personalSummary == null ? "" : personalSummary.strip();
        coreSkills = copyStrings(coreSkills);
        projectExperiences = copyExperiences(projectExperiences);
        workExperiences = copyExperiences(workExperiences);
        internshipExperiences = copyExperiences(internshipExperiences);
    }

    public static ProfileDraft empty() {
        return new ProfileDraft("", List.of(), List.of(), List.of(), List.of());
    }

    private static List<String> copyStrings(List<String> values) {
        return values == null ? List.of() : values.stream()
                .map(value -> value == null ? "" : value.strip())
                .filter(value -> !value.isBlank()).distinct().toList();
    }

    private static List<ProfileExperienceDraft> copyExperiences(
            List<ProfileExperienceDraft> values) {
        return values == null ? List.of() : values.stream()
                .filter(java.util.Objects::nonNull).toList();
    }
}
