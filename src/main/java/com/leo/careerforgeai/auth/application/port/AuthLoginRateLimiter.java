package com.leo.careerforgeai.auth.application.port;

/**
 * @program: CareerForge-AI
 * @description: 定义不保存原始邮箱和地址的登录限流边界
 * @author: Miao Zheng
 * @date: 2026-09-03
 **/
public interface AuthLoginRateLimiter {

    boolean tryAcquire(String remoteAddress, String email);
}