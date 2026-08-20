package com.leo.careerforgeai.memory.application.conversation;

/**
 * @program: CareerForge-AI
 * @description: 表示Coaching Session预期版本过期或CAS并发更新冲突
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
public final class CoachingSessionVersionConflictException extends IllegalStateException {

    public CoachingSessionVersionConflictException(String message) {
        super(message);
    }
}