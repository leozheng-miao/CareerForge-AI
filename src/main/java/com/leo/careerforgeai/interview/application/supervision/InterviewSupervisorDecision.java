package com.leo.careerforgeai.interview.application.supervision;

import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.InterviewRouteDecision;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 保存Java Supervisor输出的确定性路由、原因和可选失败码
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param routeDecision Graph允许执行的下一步路由
 * @param reason 决策原因
 * @param failureCode 仅FINALIZE_FAILURE时存在的稳定失败码
 **/
public record InterviewSupervisorDecision(
        InterviewRouteDecision routeDecision,
        InterviewSupervisorReason reason,
        InterviewFailureCode failureCode
) {

    public InterviewSupervisorDecision {
        Objects.requireNonNull(routeDecision, "routeDecision不能为空");
        Objects.requireNonNull(reason, "reason不能为空");

        boolean failureRoute = routeDecision == InterviewRouteDecision.FINALIZE_FAILURE;
        if (failureRoute != (failureCode != null)) {
            throw new IllegalArgumentException("failureCode与routeDecision不匹配");
        }
    }

    public static InterviewSupervisorDecision followUp() {
        return new InterviewSupervisorDecision(
                InterviewRouteDecision.FOLLOW_UP,
                InterviewSupervisorReason.FOLLOW_UP_RECOMMENDED,
                null
        );
    }

    public static InterviewSupervisorDecision nextQuestion() {
        return new InterviewSupervisorDecision(
                InterviewRouteDecision.NEXT_QUESTION,
                InterviewSupervisorReason.NEXT_QUESTION_ALLOWED,
                null
        );
    }

    public static InterviewSupervisorDecision generateReport(InterviewSupervisorReason reason) {
        return new InterviewSupervisorDecision(InterviewRouteDecision.GENERATE_REPORT, reason, null);
    }

    public static InterviewSupervisorDecision finalizeFailure(
            InterviewFailureCode failureCode,
            InterviewSupervisorReason reason
    ) {
        return new InterviewSupervisorDecision(
                InterviewRouteDecision.FINALIZE_FAILURE,
                reason,
                Objects.requireNonNull(failureCode, "failureCode不能为空")
        );
    }
}