package com.leo.careerforgeai.interviewflow.domain.turn;

/**
 * @program: CareerForge-AI
 * @description: 定义面试答案从草稿到冻结关闭的生命周期状态
 * @author: Miao Zheng
 * @date: 2026-09-30
 */
public enum AnswerStatus {

    DRAFT,
    SUBMITTED,
    ANALYZING,
    CLOSED,
    DISCARDED
}
