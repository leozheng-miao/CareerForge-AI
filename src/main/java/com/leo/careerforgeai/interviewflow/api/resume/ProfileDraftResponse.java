package com.leo.careerforgeai.interviewflow.api.resume;

import com.leo.careerforgeai.interviewflow.application.resume.ProfileDraft;
import com.leo.careerforgeai.interviewflow.application.resume.ProfileExperienceDraft;
import java.util.List;

public record ProfileDraftResponse(String personalSummary, List<String> coreSkills,
        List<ProfileExperienceDraft> projectExperiences,
        List<ProfileExperienceDraft> workExperiences,
        List<ProfileExperienceDraft> internshipExperiences) {
    public static ProfileDraftResponse from(ProfileDraft draft) {
        return new ProfileDraftResponse(draft.personalSummary(), draft.coreSkills(),
                draft.projectExperiences(), draft.workExperiences(), draft.internshipExperiences());
    }
}
