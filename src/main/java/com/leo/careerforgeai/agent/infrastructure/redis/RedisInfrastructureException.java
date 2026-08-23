package com.leo.careerforgeai.agent.infrastructure.redis;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 表示Redis连接、超时、命令和响应异常
 * @author: Miao Zheng
 * @date: 2026-08-21
 */
public class RedisInfrastructureException extends RuntimeException {

    private final RedisInfrastructureErrorType errorType;

    public RedisInfrastructureException(
            RedisInfrastructureErrorType errorType,
            String message
    ) {
        super(message);
        this.errorType = Objects.requireNonNull(errorType, "errorType不能为空");
    }

    public RedisInfrastructureException(
            RedisInfrastructureErrorType errorType,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.errorType = Objects.requireNonNull(errorType, "errorType不能为空");
    }

    public RedisInfrastructureErrorType errorType() {
        return errorType;
    }
}