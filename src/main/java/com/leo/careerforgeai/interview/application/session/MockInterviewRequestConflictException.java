package com.leo.careerforgeai.interview.application.session;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示相同owner和requestId被用于不同模拟面试创建请求
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
public class MockInterviewRequestConflictException extends RuntimeException {

    private final UUID existingInterviewId;

    public MockInterviewRequestConflictException(UUID existingInterviewId) {
        super("requestId已被用于不同的模拟面试创建请求");
        this.existingInterviewId = existingInterviewId;
    }

    public UUID existingInterviewId() {
        return existingInterviewId;
    }
}