
package com.leo.careerforgeai.interview.application.session;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示模拟面试客户端版本过期或数据库CAS竞争失败
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public final class MockInterviewVersionConflictException extends RuntimeException {

    private final UUID interviewId;
    private final long expectedVersion;

    public MockInterviewVersionConflictException(UUID interviewId, long expectedVersion) {
        super("模拟面试版本冲突");
        this.interviewId = Objects.requireNonNull(interviewId, "interviewId不能为空");
        this.expectedVersion = expectedVersion;
    }

    public UUID interviewId() {
        return interviewId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }
}