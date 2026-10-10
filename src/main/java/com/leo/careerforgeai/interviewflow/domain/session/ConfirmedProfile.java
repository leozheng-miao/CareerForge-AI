package com.leo.careerforgeai.interviewflow.domain.session;

import com.leo.careerforgeai.interviewflow.application.resume.ProfileDraft;
import com.leo.careerforgeai.interviewflow.application.resume.ProfileExperienceDraft;
import java.util.List;

public record ConfirmedProfile(String personalSummary, List<String> coreSkills,
        List<ProfileExperienceDraft> projectExperiences,
        List<ProfileExperienceDraft> workExperiences,
        List<ProfileExperienceDraft> internshipExperiences) {
    public ConfirmedProfile {
        ProfileDraft normalized = new ProfileDraft(personalSummary, coreSkills,
                projectExperiences, workExperiences, internshipExperiences);
        personalSummary = normalized.personalSummary();
        coreSkills = normalized.coreSkills();
        projectExperiences = normalized.projectExperiences();
        workExperiences = normalized.workExperiences();
        internshipExperiences = normalized.internshipExperiences();
    }

    public boolean hasExperience() {
        return !projectExperiences.isEmpty() || !workExperiences.isEmpty()
                || !internshipExperiences.isEmpty();
    }
}
