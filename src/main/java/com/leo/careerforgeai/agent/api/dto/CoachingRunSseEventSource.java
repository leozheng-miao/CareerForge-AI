package com.leo.careerforgeai.agent.api.dto;

/**
 * @program: CareerForge-AI
 * @description: 区分Redis短期事件和MySQL终态事实快照
 * @author: Miao Zheng
 * @date: 2026-08-21
 */
public enum CoachingRunSseEventSource {

    REDIS_STREAM,
    MYSQL_TERMINAL_SNAPSHOT
}