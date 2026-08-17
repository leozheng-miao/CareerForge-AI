package com.leo.careerforgeai.career.application;

/**
 * @program: CareerForge-AI
 * @description: 表示目标岗位草案确认时客户端版本已经过期
 * @author: Miao Zheng
 * @date: 2026-08-16
 */
public final class TargetRoleVersionConflictException
        extends RuntimeException {

    public TargetRoleVersionConflictException(String message) {
        super(message);
    }
}