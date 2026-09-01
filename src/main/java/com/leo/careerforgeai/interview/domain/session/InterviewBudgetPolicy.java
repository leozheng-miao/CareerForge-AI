package com.leo.careerforgeai.interview.domain.session;

/**
 * @program: CareerForge-AI
 * @description: 定义单场模拟面试不可由模型突破的服务端预算上限
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param maxQuestions 最大问题总数，包含追问
 * @param maxFollowUps 最大追问数
 * @param maxModelCalls 最大模型调用数
 * @param maxTotalTokens 最大累计Token软预算
 **/
public record InterviewBudgetPolicy(
        int maxQuestions,
        int maxFollowUps,
        int maxModelCalls,
        long maxTotalTokens
) {

    public InterviewBudgetPolicy {
        if (maxQuestions <= 0) {
            throw new IllegalArgumentException("maxQuestions必须大于0");
        }
        if (maxFollowUps < 0 || maxFollowUps >= maxQuestions) {
            throw new IllegalArgumentException(
                    "maxFollowUps必须大于等于0且小于maxQuestions"
            );
        }
        if (maxModelCalls <= 0) {
            throw new IllegalArgumentException("maxModelCalls必须大于0");
        }
        if (maxTotalTokens <= 0) {
            throw new IllegalArgumentException("maxTotalTokens必须大于0");
        }
    }
}