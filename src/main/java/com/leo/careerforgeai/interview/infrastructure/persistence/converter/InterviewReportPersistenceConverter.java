package com.leo.careerforgeai.interview.infrastructure.persistence.converter;

import com.leo.careerforgeai.interview.domain.report.InterviewReport;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewReportPersistenceModels.ReportRow;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewReportPersistenceModels.SuggestionRow;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 转换面试报告领域聚合、数据库行和结构化建议payload
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Component
public class InterviewReportPersistenceConverter {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private final JsonMapper jsonMapper;

    public InterviewReportPersistenceConverter(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper不能为空");
    }

    public ReportRow toReportRow(InterviewReport report) {
        Objects.requireNonNull(report, "report不能为空");
        ReportRow row = new ReportRow();
        row.setReportId(report.reportId().toString());
        row.setInterviewId(report.interviewId().toString());
        row.setOwnerId(report.ownerId().value());
        row.setReportVersion(report.reportVersion());
        row.setReportStatus(report.status().name());
        row.setStrengthsJson(serialize(report.strengths(), "strengths"));
        row.setTechnicalGapsJson(serialize(report.technicalGaps(), "technicalGaps"));
        row.setEvidenceExpressionRisksJson(serialize(
                report.evidenceExpressionRisks(), "evidenceExpressionRisks"
        ));
        row.setImprovementActionsJson(serialize(report.improvementActions(), "improvementActions"));
        row.setModelRequestId(report.modelRequestId());
        row.setPromptVersion(report.promptVersion());
        row.setInputHash(report.inputHash());
        row.setOutputHash(report.outputHash());
        row.setVersion(report.version());
        row.setCreatedAt(report.createdAt());
        row.setUpdatedAt(report.updatedAt());
        row.setDecidedAt(report.decidedAt());
        return row;
    }

    public List<SuggestionRow> toSuggestionRows(InterviewReport report) {
        Objects.requireNonNull(report, "report不能为空");
        return report.suggestions().stream().map(this::toSuggestionRow).toList();
    }

    public InterviewReport toDomain(ReportRow report, List<SuggestionRow> suggestions) {
        Objects.requireNonNull(report, "report不能为空");
        Objects.requireNonNull(suggestions, "suggestions不能为空");
        return new InterviewReport(
                UUID.fromString(report.getReportId()),
                UUID.fromString(report.getInterviewId()),
                new ActorId(report.getOwnerId()),
                report.getReportVersion(),
                InterviewReport.Status.valueOf(report.getReportStatus()),
                deserializeStrings(report.getStrengthsJson(), "strengthsJson"),
                deserializeStrings(report.getTechnicalGapsJson(), "technicalGapsJson"),
                deserializeStrings(
                        report.getEvidenceExpressionRisksJson(), "evidenceExpressionRisksJson"
                ),
                deserializeStrings(report.getImprovementActionsJson(), "improvementActionsJson"),
                suggestions.stream().map(this::toSuggestion).toList(),
                report.getModelRequestId(),
                report.getPromptVersion(),
                report.getInputHash(),
                report.getOutputHash(),
                report.getVersion(),
                report.getCreatedAt(),
                report.getUpdatedAt(),
                report.getDecidedAt()
        );
    }

    private SuggestionRow toSuggestionRow(InterviewReport.Suggestion suggestion) {
        SuggestionRow row = new SuggestionRow();
        row.setSuggestionId(suggestion.suggestionId().toString());
        row.setReportId(suggestion.reportId().toString());
        row.setInterviewId(suggestion.interviewId().toString());
        row.setOwnerId(suggestion.ownerId().value());
        row.setSuggestionType(suggestion.type().name());
        row.setSuggestionOrder(suggestion.order());
        row.setSuggestionContent(suggestion.content());
        row.setSuggestionPayloadJson(serialize(suggestion.payload(), "suggestionPayload"));
        row.setContentHash(suggestion.contentHash());
        row.setCreatedAt(suggestion.createdAt());
        return row;
    }

    private InterviewReport.Suggestion toSuggestion(SuggestionRow row) {
        InterviewReport.SuggestionType type =
                InterviewReport.SuggestionType.valueOf(row.getSuggestionType());
        InterviewReport.SuggestionPayload payload =
                deserializePayload(type, row.getSuggestionPayloadJson(), row.getSuggestionContent());

        return new InterviewReport.Suggestion(
                UUID.fromString(row.getSuggestionId()),
                UUID.fromString(row.getReportId()),
                UUID.fromString(row.getInterviewId()),
                new ActorId(row.getOwnerId()),
                type,
                row.getSuggestionOrder(),
                row.getSuggestionContent(),
                payload,
                row.getContentHash(),
                row.getCreatedAt()
        );
    }

    private InterviewReport.SuggestionPayload deserializePayload(
            InterviewReport.SuggestionType type,
            String payloadJson,
            String legacyContent
    ) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return new InterviewReport.LegacyPayload(legacyContent);
        }

        return switch (type) {
            case MEMORY_CANDIDATE -> deserialize(
                    payloadJson,
                    InterviewReport.MemoryCandidatePayload.class,
                    "memoryCandidatePayload"
            );
            case TRAINING_PLAN_ADJUSTMENT -> deserialize(
                    payloadJson,
                    InterviewReport.TrainingPlanAdjustmentPayload.class,
                    "trainingPlanAdjustmentPayload"
            );
        };
    }

    private String serialize(Object value, String fieldName) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化" + fieldName + "失败", exception);
        }
    }

    private List<String> deserializeStrings(String json, String fieldName) {
        try {
            return jsonMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("反序列化" + fieldName + "失败", exception);
        }
    }

    private <T> T deserialize(String json, Class<T> type, String fieldName) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("反序列化" + fieldName + "失败", exception);
        }
    }
}