package com.leo.careerforgeai.interviewflow.domain.session;

import java.time.Duration;

public interface InterviewPolicy {
    Duration duration();
    int topicCount();
    int maxFollowUps();
    int maxConsecutiveFollowUps();

    default boolean allowsAnotherFollowUp(int used, int consecutive) {
        if (used < 0 || consecutive < 0) throw new IllegalArgumentException("追问计数不能小于0");
        return used < maxFollowUps() && consecutive < maxConsecutiveFollowUps();
    }

    default boolean shouldGenerateReport(int submittedAnswers) {
        if (submittedAnswers < 0) throw new IllegalArgumentException("submittedAnswers不能小于0");
        return submittedAnswers > 0;
    }
}
