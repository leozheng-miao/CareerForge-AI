package com.leo.careerforgeai.interviewflow.api.session;

import com.leo.careerforgeai.interviewflow.application.session.CreateComprehensiveInterviewCommand;
import com.leo.careerforgeai.interviewflow.domain.session.ConfirmedProfile;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewDifficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateComprehensiveInterviewRequest(@NotNull UUID requestId,
        @NotBlank String direction, @NotBlank String jobDescription,
        @NotBlank String salaryRange, String personalDescription,
        ConfirmedProfile confirmedProfile, long durationSeconds,
        @NotNull InterviewDifficulty difficulty) {
    public CreateComprehensiveInterviewCommand toCommand() {
        return new CreateComprehensiveInterviewCommand(requestId, direction, jobDescription,
                salaryRange, personalDescription, confirmedProfile, durationSeconds, difficulty);
    }
}
