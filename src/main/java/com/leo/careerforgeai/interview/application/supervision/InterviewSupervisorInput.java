package com.leo.careerforgeai.interview.application.supervision;

import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.InterviewReviewPlan;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义Java Supervisor汇合评审和预算决策所需的可信输入
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param session 当前MySQL面试聚合
 * @param usage 当前MySQL预算用量
 * @param forecast 下一轮和最终报告预算预测
 * @param reviewPlan 本轮评审计划
 * @param technicalReviewId 已持久化技术评审UUID
 * @param evidenceReviewId 已持久化证据评审UUID，不适用时允许为空
 * @param followUpAllowed 当前问题是否允许追问
 * @param followUpRecommended 技术评审是否建议追问
 * @param unrecoverableFailureCode 本轮不可恢复失败码，无失败时为空
 **/
public record InterviewSupervisorInput(
        MockInterviewSession session,
        InterviewBudgetUsage usage,
        InterviewBudgetForecast forecast,
        InterviewReviewPlan reviewPlan,
        UUID technicalReviewId,
        UUID evidenceReviewId,
        boolean followUpAllowed,
        boolean followUpRecommended,
        InterviewFailureCode unrecoverableFailureCode
) {

    public InterviewSupervisorInput {
        Objects.requireNonNull(session, "session不能为空");
        Objects.requireNonNull(usage, "usage不能为空");
        Objects.requireNonNull(forecast, "forecast不能为空");
        Objects.requireNonNull(reviewPlan, "reviewPlan不能为空");

        if (session.status() != InterviewStatus.REVIEWING) {
            throw new IllegalArgumentException("Java Supervisor只能处理REVIEWING状态");
        }

        if (unrecoverableFailureCode == null) {
            Objects.requireNonNull(technicalReviewId, "technicalReviewId不能为空");
            if (reviewPlan == InterviewReviewPlan.TECHNICAL_AND_EVIDENCE) {
                Objects.requireNonNull(evidenceReviewId, "evidenceReviewId不能为空");
            }
        }
    }
}