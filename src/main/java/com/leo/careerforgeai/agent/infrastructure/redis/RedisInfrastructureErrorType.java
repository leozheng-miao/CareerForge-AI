package com.leo.careerforgeai.agent.infrastructure.redis;

/**
 * @program: CareerForge-AI
 * @description: 定义Redis基础设施的稳定失败分类
 * @author: Miao Zheng
 * @date: 2026-08-21
 */
public enum RedisInfrastructureErrorType {

    UNAVAILABLE,
    TIMED_OUT,
    COMMAND_FAILED,
    UNEXPECTED_RESPONSE,
    INVALID_DATA
}