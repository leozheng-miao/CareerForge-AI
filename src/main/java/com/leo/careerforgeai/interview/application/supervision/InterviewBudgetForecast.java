package com.leo.careerforgeai.interview.application.supervision;

/**
 * @program: CareerForge-AI
 * @description: 保存Java估算的下一轮和最终报告最低模型预算
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param nextRoundModelCalls 下一轮问题及评审所需的模型调用数
 * @param nextRoundTokens 下一轮预计Token数
 * @param reportModelCalls 最终报告所需的模型调用数
 * @param reportTokens 最终报告预计Token数
 **/
public record InterviewBudgetForecast(
        int nextRoundModelCalls,
        long nextRoundTokens,
        int reportModelCalls,
        long reportTokens
) {

    public InterviewBudgetForecast {
        if (nextRoundModelCalls <= 0) throw new IllegalArgumentException("nextRoundModelCalls必须大于0");
        if (nextRoundTokens <= 0) throw new IllegalArgumentException("nextRoundTokens必须大于0");
        if (reportModelCalls <= 0) throw new IllegalArgumentException("reportModelCalls必须大于0");
        if (reportTokens <= 0) throw new IllegalArgumentException("reportTokens必须大于0");
    }

    public int nextRoundAndReportModelCalls() {
        try {
            return Math.addExact(nextRoundModelCalls, reportModelCalls);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("模型调用预算预测溢出", exception);
        }
    }

    public long nextRoundAndReportTokens() {
        try {
            return Math.addExact(nextRoundTokens, reportTokens);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Token预算预测溢出", exception);
        }
    }
}