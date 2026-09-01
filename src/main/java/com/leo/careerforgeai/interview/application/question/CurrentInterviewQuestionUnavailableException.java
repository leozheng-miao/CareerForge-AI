package com.leo.careerforgeai.interview.application.question;

import com.leo.careerforgeai.interview.domain.session.InterviewStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 表示模拟面试当前没有处于等待用户回答状态的问题
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
public class CurrentInterviewQuestionUnavailableException extends RuntimeException {

    private final UUID interviewId;
    private final InterviewStatus interviewStatus;

    public CurrentInterviewQuestionUnavailableException(UUID interviewId, InterviewStatus interviewStatus) {
        super("当前模拟面试没有等待回答的问题");
        this.interviewId = Objects.requireNonNull(interviewId, "interviewId不能为空");
        this.interviewStatus = Objects.requireNonNull(interviewStatus, "interviewStatus不能为空");
    }

    public UUID interviewId() {
        return interviewId;
    }

    public InterviewStatus interviewStatus() {
        return interviewStatus;
    }
}