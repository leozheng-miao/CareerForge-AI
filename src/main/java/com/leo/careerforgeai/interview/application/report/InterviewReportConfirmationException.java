package com.leo.careerforgeai.interview.application.report;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 表示报告确认提交或下游应用时的资源、幂等、版本和生命周期冲突
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
public class InterviewReportConfirmationException extends RuntimeException {

    private final Reason reason;

    public InterviewReportConfirmationException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason不能为空");
    }

    public Reason reason() {
        return reason;
    }

    /**
     * @program: CareerForge-AI
     * @description: 区分报告确认提交和应用失败的稳定业务原因
     * @author: Miao Zheng
     * @date: 2026-08-30
     */
    public enum Reason {
        REPORT_NOT_FOUND,
        CONFIRMATION_NOT_FOUND,
        REQUEST_CONFLICT,
        REPORT_VERSION_CONFLICT,
        REPORT_STATE_CONFLICT,
        APPLICATION_CONFLICT
    }
}