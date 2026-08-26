package com.leo.careerforgeai.interview.domain;

/**
 * @program: CareerForge-AI
 * @description: 定义单轮面试从问题就绪到评审完成的状态机
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public enum InterviewRoundStatus {

    QUESTION_READY,
    ANSWERED,
    REVIEWED;

    public boolean isTerminal() {
        return this == REVIEWED;
    }

    public boolean canTransitionTo(InterviewRoundStatus target) {
        if (target == null) return false;

        return switch (this) {
            case QUESTION_READY ->
                    target == ANSWERED;
            case ANSWERED ->
                    target == REVIEWED;
            case REVIEWED -> false;
        };
    }
}