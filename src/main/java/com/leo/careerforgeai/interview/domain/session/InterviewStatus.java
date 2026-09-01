package com.leo.careerforgeai.interview.domain.session;

/**
 * @program: CareerForge-AI
 * @description: 定义模拟面试聚合的生命周期状态和合法迁移
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public enum InterviewStatus {

    CREATED(false),
    GENERATING_QUESTION(false),
    WAITING_FOR_ANSWER(false),
    REVIEWING(false),
    GENERATING_REPORT(false),
    AWAITING_CONFIRMATION(false),
    COMPLETED(true),
    FAILED(true),
    INTERRUPTED(true),
    CANCELLED(true);

    private final boolean terminal;

    InterviewStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean canTransitionTo(InterviewStatus target) {
        if (target == null || terminal) return false;

        if (target == FAILED
                || target == INTERRUPTED
                || target == CANCELLED) {
            return true;
        }

        return switch (this) {
            case CREATED ->
                    target == GENERATING_QUESTION;
            case GENERATING_QUESTION ->
                    target == WAITING_FOR_ANSWER;
            case WAITING_FOR_ANSWER ->
                    target == REVIEWING;
            case REVIEWING ->
                    target == GENERATING_QUESTION
                            || target == GENERATING_REPORT;
            case GENERATING_REPORT ->
                    target == AWAITING_CONFIRMATION;
            case AWAITING_CONFIRMATION ->
                    target == COMPLETED;
            case COMPLETED, FAILED, INTERRUPTED, CANCELLED -> false;
        };
    }
}