package com.leo.careerforgeai.interview.api.dto.report;

import com.leo.careerforgeai.interview.domain.report.InterviewReport;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 返回当前用户可审阅的面试复盘报告和结构化建议
 * @author: Miao Zheng
 * @date: 2026-08-30
 * @param reportId 报告UUID
 * @param interviewId 面试UUID
 * @param reportVersion 报告业务版本
 * @param status 报告确认状态
 * @param strengths 面试优势
 * @param technicalGaps 技术差距
 * @param evidenceExpressionRisks 证据表达风险
 * @param improvementActions 改进动作
 * @param suggestions 等待逐项决定的建议
 * @param promptVersion 报告Prompt版本
 * @param version 乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param decidedAt 决定完成时间
 */
public record InterviewReportResponse(
        UUID reportId,
        UUID interviewId,
        long reportVersion,
        InterviewReport.Status status,
        List<String> strengths,
        List<String> technicalGaps,
        List<String> evidenceExpressionRisks,
        List<String> improvementActions,
        List<SuggestionResponse> suggestions,
        String promptVersion,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant decidedAt
) {

    public static InterviewReportResponse from(InterviewReport report) {
        return new InterviewReportResponse(
                report.reportId(),
                report.interviewId(),
                report.reportVersion(),
                report.status(),
                report.strengths(),
                report.technicalGaps(),
                report.evidenceExpressionRisks(),
                report.improvementActions(),
                report.suggestions().stream().map(SuggestionResponse::from).toList(),
                report.promptVersion(),
                report.version(),
                report.createdAt(),
                report.updatedAt(),
                report.decidedAt()
        );
    }

    /**
     * @program: CareerForge-AI
     * @description: 返回一条可审阅且类型明确的报告建议
     * @author: Miao Zheng
     * @date: 2026-08-30
     * @param suggestionId 建议UUID
     * @param type 建议类型
     * @param order 同类型建议顺序
     * @param content 用户可见内容
     * @param executable 是否具备当前版本可执行payload
     * @param skillName Memory建议的技能名称
     * @param focusArea 训练建议的主题
     * @param adjustment 训练建议的调整要求
     * @param contentHash 结构化建议Hash
     * @param createdAt 创建时间
     */
    public record SuggestionResponse(
            UUID suggestionId,
            InterviewReport.SuggestionType type,
            int order,
            String content,
            boolean executable,
            String skillName,
            String focusArea,
            String adjustment,
            String contentHash,
            Instant createdAt
    ) {

        public static SuggestionResponse from(InterviewReport.Suggestion suggestion) {
            String skillName = null;
            String focusArea = null;
            String adjustment = null;
            boolean executable = true;

            if (suggestion.payload() instanceof InterviewReport.MemoryCandidatePayload payload) {
                skillName = payload.skillName();
            } else if (suggestion.payload()
                    instanceof InterviewReport.TrainingPlanAdjustmentPayload payload) {
                focusArea = payload.focusArea();
                adjustment = payload.adjustment();
            } else {
                executable = false;
            }

            return new SuggestionResponse(
                    suggestion.suggestionId(),
                    suggestion.type(),
                    suggestion.order(),
                    suggestion.content(),
                    executable,
                    skillName,
                    focusArea,
                    adjustment,
                    suggestion.contentHash(),
                    suggestion.createdAt()
            );
        }
    }
}