package com.leo.careerforgeai.interviewflow.api.session;

import com.leo.careerforgeai.interviewflow.application.session.UpdateInterviewSetupCommand;
import com.leo.careerforgeai.interviewflow.domain.session.ConfirmedProfile;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewDifficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateInterviewSetupRequest(@NotBlank String jobDescription,
        @NotBlank String salaryRange, String personalDescription,
        ConfirmedProfile confirmedProfile, long durationSeconds,
        @NotNull InterviewDifficulty difficulty, long expectedVersion) {
    public UpdateInterviewSetupCommand toCommand(UUID interviewId) {
        return new UpdateInterviewSetupCommand(interviewId, jobDescription, salaryRange,
                personalDescription, confirmedProfile, durationSeconds, difficulty, expectedVersion);
    }
}
