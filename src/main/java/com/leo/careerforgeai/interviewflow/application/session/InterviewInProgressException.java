package com.leo.careerforgeai.interviewflow.application.session;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 表示当前用户已有一场正式进行中的面试
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public final class InterviewInProgressException extends RuntimeException {

    private final InterviewSessionApplicationService.InProgressInterview
            recovery;

    public InterviewInProgressException(
            InterviewSessionApplicationService.InProgressInterview recovery
    ) {
        super("当前用户已有进行中的面试");
        this.recovery = Objects.requireNonNull(
                recovery,
                "recovery不能为null"
        );
    }

    public InterviewSessionApplicationService.InProgressInterview recovery() {
        return recovery;
    }
}
