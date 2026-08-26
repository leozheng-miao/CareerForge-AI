package com.leo.careerforgeai.interview.application.supervision;

import com.leo.careerforgeai.interview.domain.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewReviewPlan;
import com.leo.careerforgeai.interview.domain.InterviewRouteDecision;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证Java Supervisor的追问、下一题、报告和失败预算路由
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
class InterviewSupervisorTest {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");
    private final InterviewSupervisor supervisor = new InterviewSupervisor();

    @Test
    void shouldChooseFollowUpOrNextQuestionOnlyWhenBudgetAllows() {
        MockInterviewSession session = reviewingSession(new InterviewBudgetPolicy(5, 2, 12, 2_000));
        InterviewBudgetUsage usage = new InterviewBudgetUsage(1, 0, 3, 300);
        InterviewBudgetForecast forecast = new InterviewBudgetForecast(3, 400, 1, 200);

        InterviewSupervisorDecision followUp = supervisor.decide(input(session, usage, forecast, true, true, null));
        InterviewSupervisorDecision nextQuestion = supervisor.decide(input(session, usage, forecast, true, false, null));

        assertThat(followUp.routeDecision()).isEqualTo(InterviewRouteDecision.FOLLOW_UP);
        assertThat(nextQuestion.routeDecision()).isEqualTo(InterviewRouteDecision.NEXT_QUESTION);
    }

    @Test
    void shouldGenerateReportAtQuestionLimitOrBeforeNextRoundExhaustsBudget() {
        MockInterviewSession session = reviewingSession(new InterviewBudgetPolicy(5, 2, 10, 1_000));
        InterviewBudgetForecast forecast = new InterviewBudgetForecast(3, 300, 1, 100);

        InterviewSupervisorDecision questionLimit = supervisor.decide(input(
                session,
                new InterviewBudgetUsage(5, 2, 5, 500),
                forecast,
                false,
                false,
                null
        ));
        InterviewSupervisorDecision reservedReport = supervisor.decide(input(
                session,
                new InterviewBudgetUsage(2, 1, 8, 700),
                forecast,
                false,
                false,
                null
        ));

        assertThat(questionLimit.routeDecision()).isEqualTo(InterviewRouteDecision.GENERATE_REPORT);
        assertThat(questionLimit.reason()).isEqualTo(InterviewSupervisorReason.QUESTION_LIMIT_REACHED);
        assertThat(reservedReport.routeDecision()).isEqualTo(InterviewRouteDecision.GENERATE_REPORT);
        assertThat(reservedReport.reason()).isEqualTo(InterviewSupervisorReason.NEXT_ROUND_BUDGET_INSUFFICIENT);
    }

    @Test
    void shouldFinalizeFailureWhenReportCannotRunOrRoundAlreadyFailed() {
        MockInterviewSession session = reviewingSession(new InterviewBudgetPolicy(5, 2, 10, 1_000));
        InterviewBudgetForecast forecast = new InterviewBudgetForecast(3, 300, 1, 100);

        InterviewSupervisorDecision noReportBudget = supervisor.decide(input(
                session,
                new InterviewBudgetUsage(2, 1, 10, 1_000),
                forecast,
                false,
                false,
                null
        ));
        InterviewSupervisorDecision modelFailure = supervisor.decide(input(
                session,
                new InterviewBudgetUsage(1, 0, 3, 300),
                forecast,
                false,
                false,
                InterviewFailureCode.MODEL_OUTPUT_INVALID
        ));

        assertThat(noReportBudget.routeDecision()).isEqualTo(InterviewRouteDecision.FINALIZE_FAILURE);
        assertThat(noReportBudget.failureCode()).isEqualTo(InterviewFailureCode.BUDGET_EXHAUSTED);
        assertThat(modelFailure.routeDecision()).isEqualTo(InterviewRouteDecision.FINALIZE_FAILURE);
        assertThat(modelFailure.failureCode()).isEqualTo(InterviewFailureCode.MODEL_OUTPUT_INVALID);
    }

    private InterviewSupervisorInput input(
            MockInterviewSession session,
            InterviewBudgetUsage usage,
            InterviewBudgetForecast forecast,
            boolean followUpAllowed,
            boolean followUpRecommended,
            InterviewFailureCode failureCode
    ) {
        return new InterviewSupervisorInput(
                session,
                usage,
                forecast,
                InterviewReviewPlan.TECHNICAL_AND_EVIDENCE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                followUpAllowed,
                followUpRecommended,
                failureCode
        );
    }

    private MockInterviewSession reviewingSession(InterviewBudgetPolicy policy) {
        return MockInterviewSession.create(
                        UUID.randomUUID(),
                        new ActorId("owner-a"),
                        UUID.randomUUID(),
                        "a".repeat(64),
                        InterviewMode.TARGETED_MOCK,
                        UUID.randomUUID(),
                        "b".repeat(64),
                        policy,
                        NOW
                )
                .startQuestionGeneration(NOW)
                .waitForAnswer(NOW)
                .startReview(NOW);
    }
}