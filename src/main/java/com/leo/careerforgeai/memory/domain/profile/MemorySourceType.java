package com.leo.careerforgeai.memory.domain.profile;

/**
 * @program: CareerForge-AI
 * @description: 定义Memory候选允许引用的可信业务来源类型
 * @author: Miao Zheng
 * @date: 2026-08-12
 */
public enum MemorySourceType {

    CONVERSATION_TURN,
    AGENT_RUN,
    JOB_DOCUMENT,
    PROJECT_EVIDENCE,
    INTERVIEW_REPORT
}