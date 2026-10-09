package com.leo.careerforgeai.interviewflow.application.report;

import java.util.Objects;
import java.util.UUID;

/** 表示面试存在且属于当前用户，但该场次按规则没有创建报告。 */
public final class InterviewReportNotFoundException extends RuntimeException {
    private final UUID interviewId;

    public InterviewReportNotFoundException(UUID interviewId) {
        super("面试报告不存在");
        this.interviewId = Objects.requireNonNull(interviewId, "interviewId不能为null");
    }

    public UUID interviewId() {
        return interviewId;
    }
}
