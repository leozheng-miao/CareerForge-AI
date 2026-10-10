package com.leo.careerforgeai.interviewflow.application.session;

import com.leo.careerforgeai.interviewflow.domain.session.ConfirmedProfile;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewDifficulty;
import com.leo.careerforgeai.interviewflow.domain.session.SalaryRangeBand;
import java.util.Objects;
import java.util.UUID;

public record CreateComprehensiveInterviewCommand(UUID requestId, String direction,
        String jobDescription, String salaryRange, String personalDescription,
        ConfirmedProfile confirmedProfile, long durationSeconds,
        InterviewDifficulty difficulty) {
    public CreateComprehensiveInterviewCommand {
        Objects.requireNonNull(requestId, "requestId不能为空");
        direction = require(direction, "direction", 64);
        jobDescription = require(jobDescription, "jobDescription", 30000);
        salaryRange = SalaryRangeBand.requireSupported(salaryRange);
        personalDescription = optional(personalDescription, 5000, "personalDescription");
        Objects.requireNonNull(difficulty, "difficulty不能为空");
        new com.leo.careerforgeai.interviewflow.domain.session.ComprehensiveInterviewPolicy(durationSeconds);
    }

    static String require(String value, String field, int max) {
        String normalized = optional(value, max, field);
        if (normalized == null || normalized.isBlank()) throw new IllegalArgumentException(field + "不能为空");
        return normalized;
    }

    static String optional(String value, int max, String field) {
        if (value == null) return null;
        String normalized = value.strip();
        if (normalized.length() > max) throw new IllegalArgumentException(field + "超过长度限制");
        return normalized;
    }
}
