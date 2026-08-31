package com.leo.careerforgeai.interview.api.dto.report;

import com.leo.careerforgeai.interview.domain.InterviewReportConfirmation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回报告逐项决定及其Memory或训练计划应用结果
 * @author: Miao Zheng
 * @date: 2026-08-31
 * @param confirmationId 确认单UUID
 * @param reportId 报告UUID
 * @param interviewId 面试UUID
 * @param requestId 客户端幂等请求UUID
 * @param expectedVersion 确认时使用的报告乐观锁版本
 * @param status 整张确认单应用状态
 * @param decisions 逐项应用结果
 * @param failureCode 聚合失败码
 * @param version 确认单乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param applicationFinishedAt 应用完成时间
 */
public record InterviewReportConfirmationResponse(
        UUID confirmationId,
        UUID reportId,
        UUID interviewId,
        UUID requestId,
        long expectedVersion,
        InterviewReportConfirmation.Status status,
        List<DecisionResponse> decisions,
        String failureCode,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant applicationFinishedAt
) {

    public static InterviewReportConfirmationResponse from(
            InterviewReportConfirmation confirmation
    ) {
        return new InterviewReportConfirmationResponse(
                confirmation.confirmationId(),
                confirmation.reportId(),
                confirmation.interviewId(),
                confirmation.requestId(),
                confirmation.expectedReportVersion(),
                confirmation.status(),
                confirmation.decisions().stream().map(DecisionResponse::from).toList(),
                confirmation.failureCode(),
                confirmation.version(),
                confirmation.createdAt(),
                confirmation.updatedAt(),
                confirmation.applicationFinishedAt()
        );
    }

    /**
     * @program: CareerForge-AI
     * @description: 返回单条建议的用户决定和下游应用结果
     * @author: Miao Zheng
     * @date: 2026-08-30
     * @param decisionId 决定UUID
     * @param suggestionId 报告建议UUID
     * @param decisionType 用户决定
     * @param applicationStatus 下游应用状态
     * @param outputReferenceId Memory或训练计划UUID
     * @param failureCode 单条应用失败码
     * @param updatedAt 更新时间
     * @param finishedAt 应用结束时间
     */
    public record DecisionResponse(
            UUID decisionId,
            UUID suggestionId,
            InterviewReportConfirmation.DecisionType decisionType,
            InterviewReportConfirmation.ApplicationStatus applicationStatus,
            UUID outputReferenceId,
            String failureCode,
            Instant updatedAt,
            Instant finishedAt
    ) {

        public static DecisionResponse from(
                InterviewReportConfirmation.Decision decision
        ) {
            return new DecisionResponse(
                    decision.decisionId(),
                    decision.suggestionId(),
                    decision.decisionType(),
                    decision.applicationStatus(),
                    decision.outputReferenceId(),
                    decision.failureCode(),
                    decision.updatedAt(),
                    decision.finishedAt()
            );
        }
    }
}