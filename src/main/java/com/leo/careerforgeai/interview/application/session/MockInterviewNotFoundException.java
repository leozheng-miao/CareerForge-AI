package com.leo.careerforgeai.interview.application.session;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示当前用户范围内不存在指定模拟面试
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public final class MockInterviewNotFoundException extends RuntimeException {

    private final UUID interviewId;

    public MockInterviewNotFoundException(UUID interviewId) {
        super("模拟面试不存在");
        this.interviewId = Objects.requireNonNull(interviewId, "interviewId不能为空");
    }

    public UUID interviewId() {
        return interviewId;
    }
}