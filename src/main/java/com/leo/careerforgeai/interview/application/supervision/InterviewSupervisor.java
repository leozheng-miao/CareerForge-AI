package com.leo.careerforgeai.interview.application.supervision;

import com.leo.careerforgeai.interview.domain.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 根据MySQL事实、评审完整性和服务端预算确定下一步Graph路由
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Component
public class InterviewSupervisor {

    public InterviewSupervisorDecision decide(InterviewSupervisorInput input) {
        Objects.requireNonNull(input, "input不能为空");

        if (input.unrecoverableFailureCode() != null) {
            return InterviewSupervisorDecision.finalizeFailure(
                    input.unrecoverableFailureCode(),
                    InterviewSupervisorReason.UNRECOVERABLE_FAILURE
            );
        }

        InterviewBudgetPolicy policy = input.session().budgetPolicy();
        InterviewBudgetUsage usage = input.usage();
        InterviewBudgetForecast forecast = input.forecast();

        boolean reportBudgetAvailable = usage.canReserve(
                policy,
                forecast.reportModelCalls(),
                forecast.reportTokens()
        );

        if (!reportBudgetAvailable) {
            return InterviewSupervisorDecision.finalizeFailure(
                    InterviewFailureCode.BUDGET_EXHAUSTED,
                    InterviewSupervisorReason.REPORT_BUDGET_INSUFFICIENT
            );
        }

        if (usage.questionLimitReached(policy)) {
            return InterviewSupervisorDecision.generateReport(
                    InterviewSupervisorReason.QUESTION_LIMIT_REACHED
            );
        }

        boolean nextRoundAndReportBudgetAvailable = usage.canReserve(
                policy,
                forecast.nextRoundAndReportModelCalls(),
                forecast.nextRoundAndReportTokens()
        );

        if (!nextRoundAndReportBudgetAvailable) {
            return InterviewSupervisorDecision.generateReport(
                    InterviewSupervisorReason.NEXT_ROUND_BUDGET_INSUFFICIENT
            );
        }

        if (input.followUpAllowed()
                && input.followUpRecommended()
                && usage.followUpAvailable(policy)) {
            return InterviewSupervisorDecision.followUp();
        }

        return InterviewSupervisorDecision.nextQuestion();
    }
}