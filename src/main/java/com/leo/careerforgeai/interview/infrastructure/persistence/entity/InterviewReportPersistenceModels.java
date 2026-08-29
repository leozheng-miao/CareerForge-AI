package com.leo.careerforgeai.interview.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 集中定义面试报告及报告建议的数据库映射模型
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
public class InterviewReportPersistenceModels {

    private InterviewReportPersistenceModels() {
    }

    /**
     * @program: CareerForge-AI
     * @description: 映射interview_report报告事实和确认状态
     * @author: Miao Zheng
     * @date: 2026-08-29
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ReportRow {

        private String reportId;
        private String interviewId;
        private String ownerId;
        private Long reportVersion;
        private String reportStatus;
        private String strengthsJson;
        private String technicalGapsJson;
        private String evidenceExpressionRisksJson;
        private String improvementActionsJson;
        private String modelRequestId;
        private String promptVersion;
        private String inputHash;
        private String outputHash;
        private Long version;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant decidedAt;
    }

    /**
     * @program: CareerForge-AI
     * @description: 映射interview_report_suggestion结构化待确认建议
     * @author: Miao Zheng
     * @date: 2026-08-29
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SuggestionRow {

        private String suggestionId;
        private String reportId;
        private String interviewId;
        private String ownerId;
        private String suggestionType;
        private Integer suggestionOrder;
        private String suggestionContent;
        private String suggestionPayloadJson;
        private String contentHash;
        private Instant createdAt;
    }
}