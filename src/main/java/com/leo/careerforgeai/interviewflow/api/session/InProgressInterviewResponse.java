package com.leo.careerforgeai.interviewflow.api.session;

import com.leo.careerforgeai.interviewflow.application.session.InterviewSessionApplicationService.InProgressInterview;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回进行中面试的最小安全恢复信息
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public record InProgressInterviewResponse(
        String conflictCode,
        UUID interviewId,
        InterviewMode mode,
        int currentQuestionNumber,
        long remainingSeconds
) {

    public static final String CONFLICT_CODE = "INTERVIEW_IN_PROGRESS";

    public static InProgressInterviewResponse from(
            InProgressInterview interview
    ) {
        Objects.requireNonNull(interview, "interview不能为null");
        return new InProgressInterviewResponse(
                CONFLICT_CODE,
                interview.interviewId(),
                interview.mode(),
                interview.currentQuestionNumber(),
                interview.remainingSeconds()
        );
    }
}
