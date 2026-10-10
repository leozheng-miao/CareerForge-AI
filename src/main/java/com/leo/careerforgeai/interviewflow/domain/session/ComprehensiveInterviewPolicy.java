package com.leo.careerforgeai.interviewflow.domain.session;

import java.time.Duration;

public final class ComprehensiveInterviewPolicy implements InterviewPolicy {
    private final Duration duration;

    public ComprehensiveInterviewPolicy(long durationSeconds) {
        if (durationSeconds != 3600 && durationSeconds != 5400) {
            throw new IllegalArgumentException("综合面试时长只能为60或90分钟");
        }
        this.duration = Duration.ofSeconds(durationSeconds);
    }

    @Override public Duration duration() { return duration; }
    @Override public int topicCount() { return 6; }
    @Override public int maxFollowUps() { return duration.toSeconds() == 5400 ? 8 : 4; }
    @Override public int maxConsecutiveFollowUps() { return 2; }
}
