package com.leo.careerforgeai.interview.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 集中定义报告确认单和逐项决定的数据库映射模型
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
public class InterviewReportConfirmationPersistenceModels {

    private InterviewReportConfirmationPersistenceModels() {
    }

    /**
     * @program: CareerForge-AI
     * @description: 映射interview_report_confirmation确认单
     * @author: Miao Zheng
     * @date: 2026-08-29
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ConfirmationRow {

        private String confirmationId;
        private String reportId;
        private String interviewId;
        private String ownerId;
        private String requestId;
        private String requestFingerprint;
        private Long expectedReportVersion;
        private String confirmationStatus;
        private String failureCode;
        private Long version;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant applicationFinishedAt;
    }

    /**
     * @program: CareerForge-AI
     * @description: 映射interview_report_decision逐项决定和应用结果
     * @author: Miao Zheng
     * @date: 2026-08-29
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class DecisionRow {

        private String decisionId;
        private String confirmationId;
        private String suggestionId;
        private String reportId;
        private String interviewId;
        private String ownerId;
        private String decisionType;
        private String applicationStatus;
        private String outputReferenceId;
        private String failureCode;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant finishedAt;
    }
}