package com.leo.careerforgeai.interview.application.session;

import com.leo.careerforgeai.interview.domain.session.InterviewStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示模拟面试已经进入不可取消的业务终态
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
public class MockInterviewCancellationConflictException extends RuntimeException {

    private final UUID interviewId;
    private final InterviewStatus status;

    public MockInterviewCancellationConflictException(UUID interviewId, InterviewStatus status) {
        super("当前面试状态不允许取消");
        this.interviewId = Objects.requireNonNull(interviewId, "interviewId不能为空");
        this.status = Objects.requireNonNull(status, "status不能为空");
    }

    public UUID interviewId() {
        return interviewId;
    }

    public InterviewStatus status() {
        return status;
    }
}