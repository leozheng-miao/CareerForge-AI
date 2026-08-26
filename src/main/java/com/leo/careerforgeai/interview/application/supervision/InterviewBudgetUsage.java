package com.leo.careerforgeai.interview.application.supervision;

import com.leo.careerforgeai.interview.domain.InterviewBudgetPolicy;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 保存Java从MySQL业务事实汇总出的面试预算用量
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param questionsAsked 已持久化问题数，包含追问
 * @param followUpsUsed 已持久化追问数
 * @param modelCallsUsed 已登记模型调用数
 * @param totalTokensUsed 已记录累计Token数
 **/
public record InterviewBudgetUsage(
        int questionsAsked,
        int followUpsUsed,
        int modelCallsUsed,
        long totalTokensUsed
) {

    public InterviewBudgetUsage {
        if (questionsAsked < 0) throw new IllegalArgumentException("questionsAsked不能小于0");
        if (followUpsUsed < 0 || followUpsUsed > questionsAsked) {
            throw new IllegalArgumentException("followUpsUsed必须位于0和questionsAsked之间");
        }
        if (modelCallsUsed < 0) throw new IllegalArgumentException("modelCallsUsed不能小于0");
        if (totalTokensUsed < 0) throw new IllegalArgumentException("totalTokensUsed不能小于0");
    }

    public boolean questionLimitReached(InterviewBudgetPolicy policy) {
        return questionsAsked >= Objects.requireNonNull(policy, "policy不能为空").maxQuestions();
    }

    public boolean followUpAvailable(InterviewBudgetPolicy policy) {
        return followUpsUsed < Objects.requireNonNull(policy, "policy不能为空").maxFollowUps();
    }

    public boolean canReserve(InterviewBudgetPolicy policy, int additionalModelCalls, long additionalTokens) {
        Objects.requireNonNull(policy, "policy不能为空");
        if (additionalModelCalls < 0) throw new IllegalArgumentException("additionalModelCalls不能小于0");
        if (additionalTokens < 0) throw new IllegalArgumentException("additionalTokens不能小于0");

        try {
            int projectedModelCalls = Math.addExact(modelCallsUsed, additionalModelCalls);
            long projectedTokens = Math.addExact(totalTokensUsed, additionalTokens);
            return projectedModelCalls <= policy.maxModelCalls() && projectedTokens <= policy.maxTotalTokens();
        } catch (ArithmeticException exception) {
            return false;
        }
    }
}