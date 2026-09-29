package com.leo.careerforgeai.interviewflow.domain.session;

/**
 * @program: CareerForge-AI
 * @description: 定义新面试会话的主生命周期阶段
 * @author: Miao Zheng
 * @date: 2026-09-30
 */
public enum SessionStage {

    PREPARING(false),
    READY(false),
    INTERVIEWING(false),
    GENERATING_REPORT(false),
    COMPLETED(true),
    FAILED(true);

    private final boolean terminal;

    SessionStage(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
