package com.leo.careerforgeai.interviewflow.domain.session;

import java.time.Duration;

/**
 * @program: CareerForge-AI
 * @description: 定义快速面试不可由模型突破的固定业务规则
 * @author: Miao Zheng
 * @date: 2026-09-30
 */
public final class QuickInterviewPolicy {

    private static final Duration DURATION = Duration.ofMinutes(60);
    private static final int TOPIC_COUNT = 8;
    private static final int MAX_FOLLOW_UPS = 4;
    private static final int MAX_CONSECUTIVE_FOLLOW_UPS = 2;

    public Duration duration() {
        return DURATION;
    }

    public int topicCount() {
        return TOPIC_COUNT;
    }

    public int maxFollowUps() {
        return MAX_FOLLOW_UPS;
    }

    public int maxConsecutiveFollowUps() {
        return MAX_CONSECUTIVE_FOLLOW_UPS;
    }

    public boolean allowsAnotherFollowUp(
            int usedFollowUps,
            int consecutiveFollowUps
    ) {
        requireNonNegative(usedFollowUps, "usedFollowUps");
        requireNonNegative(consecutiveFollowUps, "consecutiveFollowUps");

        return usedFollowUps < MAX_FOLLOW_UPS
                && consecutiveFollowUps < MAX_CONSECUTIVE_FOLLOW_UPS;
    }

    public boolean shouldGenerateReport(int submittedAnswerCount) {
        requireNonNegative(submittedAnswerCount, "submittedAnswerCount");
        return submittedAnswerCount > 0;
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + "不能小于0");
        }
    }
}
