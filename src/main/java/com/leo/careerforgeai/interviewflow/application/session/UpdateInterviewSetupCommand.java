package com.leo.careerforgeai.interviewflow.application.session;

import com.leo.careerforgeai.interviewflow.domain.session.ConfirmedProfile;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewDifficulty;
import com.leo.careerforgeai.interviewflow.domain.session.SalaryRangeBand;
import java.util.Objects;
import java.util.UUID;

public record UpdateInterviewSetupCommand(UUID interviewId, String jobDescription,
        String salaryRange, String personalDescription, ConfirmedProfile confirmedProfile,
        long durationSeconds, InterviewDifficulty difficulty, long expectedVersion) {
    public UpdateInterviewSetupCommand {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        jobDescription = CreateComprehensiveInterviewCommand.require(jobDescription, "jobDescription", 30000);
        salaryRange = SalaryRangeBand.requireSupported(salaryRange);
        personalDescription = CreateComprehensiveInterviewCommand.optional(personalDescription, 5000, "personalDescription");
        Objects.requireNonNull(difficulty, "difficulty不能为空");
        new com.leo.careerforgeai.interviewflow.domain.session.ComprehensiveInterviewPolicy(durationSeconds);
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion不能小于0");
    }
}
