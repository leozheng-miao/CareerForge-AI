package com.leo.careerforgeai.interviewflow.application.preparation;

import com.leo.careerforgeai.interviewflow.application.resume.ProfileExperienceDraft;
import com.leo.careerforgeai.interviewflow.domain.session.ConfirmedProfile;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 读取用户确认画像；输出仍是待面试验证的主张，不提升为事实。 */
@Component
public final class ProfileEvidenceAnalyzer {
    private final JsonMapper json;

    public ProfileEvidenceAnalyzer(JsonMapper json) {
        this.json = Objects.requireNonNull(json);
    }

    public ProfileSignals analyze(String confirmedProfileJson) {
        if (confirmedProfileJson == null || confirmedProfileJson.isBlank()) {
            return ProfileSignals.empty();
        }
        try {
            ConfirmedProfile profile = json.readValue(
                    confirmedProfileJson, ConfirmedProfile.class);
            List<ExperienceSignal> experiences = new ArrayList<>();
            add(experiences, "项目", profile.projectExperiences());
            add(experiences, "工作", profile.workExperiences());
            add(experiences, "实习", profile.internshipExperiences());
            return new ProfileSignals(profile.personalSummary(),
                    profile.coreSkills(), experiences);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("确认画像JSON不合法", exception);
        }
    }

    private static void add(List<ExperienceSignal> target, String category,
            List<ProfileExperienceDraft> values) {
        for (ProfileExperienceDraft value : values) {
            String label = value.name().isBlank() ? category + "经历" : value.name();
            target.add(new ExperienceSignal(category, label, value.role(),
                    value.description(), value.technologies()));
        }
    }

    public record ProfileSignals(String personalSummary, List<String> skills,
            List<ExperienceSignal> experiences) {
        public ProfileSignals {
            personalSummary = personalSummary == null ? "" : personalSummary;
            skills = skills == null ? List.of() : List.copyOf(skills);
            experiences = experiences == null ? List.of() : List.copyOf(experiences);
        }

        public static ProfileSignals empty() {
            return new ProfileSignals("", List.of(), List.of());
        }
    }

    public record ExperienceSignal(String category, String label, String role,
            String description, List<String> technologies) {
        public ExperienceSignal {
            category = Objects.requireNonNull(category);
            label = Objects.requireNonNull(label);
            role = role == null ? "" : role;
            description = description == null ? "" : description;
            technologies = technologies == null ? List.of() : List.copyOf(technologies);
        }
    }
}
