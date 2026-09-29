package com.leo.careerforgeai.interviewflow.domain.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证快速面试第一交付的固定时长、覆盖目标、追问预算和报告门槛
 * @author: Miao Zheng
 * @date: 2026-09-28
 */
class QuickInterviewPolicyTest {

    private final QuickInterviewPolicy policy = new QuickInterviewPolicy();

    @Test
    void shouldUseConfirmedQuickInterviewLimits() {
        assertThat(policy.duration()).isEqualTo(Duration.ofMinutes(60));
        assertThat(policy.topicCount()).isEqualTo(8);
        assertThat(policy.maxFollowUps()).isEqualTo(4);
        assertThat(policy.maxConsecutiveFollowUps()).isEqualTo(2);
    }

    @ParameterizedTest(name = "used={0}, consecutive={1}, allowed={2}")
    @CsvSource({
            "0, 0, true",
            "3, 1, true",
            "4, 0, false",
            "0, 2, false",
            "4, 2, false"
    })
    void shouldAllowAnotherFollowUpOnlyWithinBothBudgets(
            int usedFollowUps,
            int consecutiveFollowUps,
            boolean expected
    ) {
        assertThat(policy.allowsAnotherFollowUp(
                usedFollowUps,
                consecutiveFollowUps
        )).isEqualTo(expected);
    }

    @ParameterizedTest(name = "submittedAnswers={0}, reportRequired={1}")
    @CsvSource({
            "0, false",
            "1, true",
            "8, true"
    })
    void shouldGenerateReportOnlyWhenAtLeastOneAnswerWasSubmitted(
            int submittedAnswerCount,
            boolean expected
    ) {
        assertThat(policy.shouldGenerateReport(submittedAnswerCount))
                .isEqualTo(expected);
    }
}
