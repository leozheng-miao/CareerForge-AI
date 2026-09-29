package com.leo.careerforgeai.interviewflow.domain.turn;

/**
 * @program: CareerForge-AI
 * @description: 定义面试题目的生命周期状态
 * @author: Miao Zheng
 * @date: 2026-09-30
 */
public enum QuestionStatus {

    RESERVED,
    PRESENTED,
    CLOSED,
    SKIPPED,
    INVALIDATED
}
