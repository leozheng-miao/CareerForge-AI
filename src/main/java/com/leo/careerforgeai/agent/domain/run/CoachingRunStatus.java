package com.leo.careerforgeai.agent.domain.run;

/**
 * @program: CareerForge-AI
 * @description: 定义耐久Career Coach Run的生命周期状态和合法迁移
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
public enum CoachingRunStatus {

    RECEIVED(false),
    ACCEPTED(false),
    RUNNING(false),
    SUCCEEDED(true),
    FAILED(true),
    TIMED_OUT(true),
    REJECTED(true),
    INTERRUPTED(true);

    private final boolean terminal;

    CoachingRunStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean canTransitionTo(CoachingRunStatus target) {
        if (target == null || terminal) return false;

        return switch (this) {
            case RECEIVED -> target == ACCEPTED
                    || target == REJECTED
                    || target == INTERRUPTED;
            case ACCEPTED -> target == RUNNING
                    || target == REJECTED
                    || target == INTERRUPTED;
            case RUNNING -> target == SUCCEEDED
                    || target == FAILED
                    || target == TIMED_OUT
                    || target == INTERRUPTED;
            case SUCCEEDED, FAILED, TIMED_OUT, REJECTED, INTERRUPTED -> false;
        };
    }
}