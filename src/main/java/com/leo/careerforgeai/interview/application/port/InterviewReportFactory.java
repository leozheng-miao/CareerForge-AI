package com.leo.careerforgeai.interview.application.port;

import com.leo.careerforgeai.interview.application.model.contract.InterviewReportDraft;
import com.leo.careerforgeai.interview.application.model.contract.InterviewReportSuggestionDraft;
import com.leo.careerforgeai.interview.domain.InterviewReport;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.leo.careerforgeai.interview.application.model.contract.InterviewReportInput;
import com.leo.careerforgeai.interview.application.report.InterviewReportMemoryCandidatePolicy;

/**
 * @program: CareerForge-AI
 * @description: 将通过角色契约校验的复盘结果转换为待用户确认的结构化报告聚合
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Component
public class InterviewReportFactory {

    private final JsonMapper jsonMapper;
    private final InterviewReportMemoryCandidatePolicy memoryCandidatePolicy;

    public InterviewReportFactory(
            JsonMapper jsonMapper,
            InterviewReportMemoryCandidatePolicy memoryCandidatePolicy
    ) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper不能为空");
        this.memoryCandidatePolicy = Objects.requireNonNull(memoryCandidatePolicy, "memoryCandidatePolicy不能为空");
    }

    public InterviewReport create(
            UUID reportId,
            UUID interviewId,
            ActorId ownerId,
            String inputHash,
            InterviewReportInput input,
            InterviewRoleModelGateway.Result<InterviewReportDraft> result,
            Instant createdAt
    ) {
        Objects.requireNonNull(reportId, "reportId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(result, "result不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");
        Objects.requireNonNull(input, "input不能为空");

        InterviewReportDraft draft = memoryCandidatePolicy.filter(input, result.output());
        List<String> strengths = normalizeItems(draft.strengths(), "strengths");
        List<String> technicalGaps = normalizeItems(draft.technicalGaps(), "technicalGaps");
        List<String> evidenceRisks = normalizeItems(
                draft.evidenceExpressionRisks(), "evidenceExpressionRisks"
        );
        List<String> improvementActions = normalizeItems(
                draft.improvementActions(), "improvementActions"
        );
        List<InterviewReportSuggestionDraft.MemoryCandidate> memoryCandidates =
                normalizeMemoryCandidates(draft.proposedMemoryCandidates());
        List<InterviewReportSuggestionDraft.TrainingPlanAdjustment> trainingAdjustments =
                normalizeTrainingAdjustments(draft.proposedTrainingPlanAdjustments());

        List<InterviewReport.Suggestion> suggestions = createSuggestions(
                reportId, interviewId, ownerId, memoryCandidates, trainingAdjustments, createdAt
        );
        String outputHash = outputHash(
                strengths,
                technicalGaps,
                evidenceRisks,
                improvementActions,
                memoryCandidates,
                trainingAdjustments
        );

        return InterviewReport.pendingConfirmation(
                reportId,
                interviewId,
                ownerId,
                strengths,
                technicalGaps,
                evidenceRisks,
                improvementActions,
                suggestions,
                result.requestId(),
                result.promptVersion(),
                inputHash,
                outputHash,
                createdAt
        );
    }

    private List<InterviewReport.Suggestion> createSuggestions(
            UUID reportId,
            UUID interviewId,
            ActorId ownerId,
            List<InterviewReportSuggestionDraft.MemoryCandidate> memoryCandidates,
            List<InterviewReportSuggestionDraft.TrainingPlanAdjustment> trainingAdjustments,
            Instant createdAt
    ) {
        List<InterviewReport.Suggestion> suggestions = new ArrayList<>(
                memoryCandidates.size() + trainingAdjustments.size()
        );

        for (int index = 0; index < memoryCandidates.size(); index++) {
            InterviewReportSuggestionDraft.MemoryCandidate candidate = memoryCandidates.get(index);
            InterviewReport.MemoryCandidatePayload payload =
                    new InterviewReport.MemoryCandidatePayload(candidate.skillName(), candidate.content());
            suggestions.add(createSuggestion(
                    reportId,
                    interviewId,
                    ownerId,
                    InterviewReport.SuggestionType.MEMORY_CANDIDATE,
                    index + 1,
                    payload,
                    createdAt
            ));
        }

        for (int index = 0; index < trainingAdjustments.size(); index++) {
            InterviewReportSuggestionDraft.TrainingPlanAdjustment adjustment =
                    trainingAdjustments.get(index);
            InterviewReport.TrainingPlanAdjustmentPayload payload =
                    new InterviewReport.TrainingPlanAdjustmentPayload(
                            adjustment.focusArea(), adjustment.adjustment()
                    );
            suggestions.add(createSuggestion(
                    reportId,
                    interviewId,
                    ownerId,
                    InterviewReport.SuggestionType.TRAINING_PLAN_ADJUSTMENT,
                    index + 1,
                    payload,
                    createdAt
            ));
        }
        return List.copyOf(suggestions);
    }

    private InterviewReport.Suggestion createSuggestion(
            UUID reportId,
            UUID interviewId,
            ActorId ownerId,
            InterviewReport.SuggestionType type,
            int order,
            InterviewReport.SuggestionPayload payload,
            Instant createdAt
    ) {
        String payloadJson = serialize(payload, "suggestionPayload");
        return new InterviewReport.Suggestion(
                UUID.randomUUID(),
                reportId,
                interviewId,
                ownerId,
                type,
                order,
                payload.displayContent(),
                payload,
                sha256(payloadJson),
                createdAt
        );
    }

    private List<InterviewReportSuggestionDraft.MemoryCandidate> normalizeMemoryCandidates(
            List<InterviewReportSuggestionDraft.MemoryCandidate> values
    ) {
        Objects.requireNonNull(values, "proposedMemoryCandidates不能为空");
        List<InterviewReportSuggestionDraft.MemoryCandidate> normalized = new ArrayList<>(values.size());
        Set<String> skills = new HashSet<>();

        for (InterviewReportSuggestionDraft.MemoryCandidate value : values) {
            Objects.requireNonNull(value, "proposedMemoryCandidates不能包含空元素");
            String skillName = normalizeText(value.skillName(), "skillName", 128);
            String content = normalizeText(value.content(), "memoryContent", 1_000);
            if (!skills.add(normalizeKey(skillName))) {
                throw new IllegalArgumentException("proposedMemoryCandidates规范化后包含重复skillName");
            }
            normalized.add(new InterviewReportSuggestionDraft.MemoryCandidate(skillName, content));
        }
        return List.copyOf(normalized);
    }

    private List<InterviewReportSuggestionDraft.TrainingPlanAdjustment> normalizeTrainingAdjustments(
            List<InterviewReportSuggestionDraft.TrainingPlanAdjustment> values
    ) {
        Objects.requireNonNull(values, "proposedTrainingPlanAdjustments不能为空");
        List<InterviewReportSuggestionDraft.TrainingPlanAdjustment> normalized =
                new ArrayList<>(values.size());
        Set<String> focusAreas = new HashSet<>();

        for (InterviewReportSuggestionDraft.TrainingPlanAdjustment value : values) {
            Objects.requireNonNull(value, "proposedTrainingPlanAdjustments不能包含空元素");
            String focusArea = normalizeText(value.focusArea(), "focusArea", 128);
            String adjustment = normalizeText(value.adjustment(), "adjustment", 1_000);
            if (!focusAreas.add(normalizeKey(focusArea))) {
                throw new IllegalArgumentException(
                        "proposedTrainingPlanAdjustments规范化后包含重复focusArea"
                );
            }
            normalized.add(
                    new InterviewReportSuggestionDraft.TrainingPlanAdjustment(focusArea, adjustment)
            );
        }
        return List.copyOf(normalized);
    }

    private String outputHash(
            List<String> strengths,
            List<String> technicalGaps,
            List<String> evidenceRisks,
            List<String> improvementActions,
            List<InterviewReportSuggestionDraft.MemoryCandidate> memoryCandidates,
            List<InterviewReportSuggestionDraft.TrainingPlanAdjustment> trainingAdjustments
    ) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("schemaVersion", 2);
        output.put("strengths", strengths);
        output.put("technicalGaps", technicalGaps);
        output.put("evidenceExpressionRisks", evidenceRisks);
        output.put("improvementActions", improvementActions);
        output.put("proposedMemoryCandidates", memoryCandidates);
        output.put("proposedTrainingPlanAdjustments", trainingAdjustments);
        return sha256(serialize(output, "reportOutput"));
    }

    private List<String> normalizeItems(List<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + "不能为空");
        List<String> normalized = values.stream()
                .map(value -> normalizeText(value, fieldName, 1_000))
                .toList();
        if (new HashSet<>(normalized).size() != normalized.size()) {
            throw new IllegalArgumentException(fieldName + "规范化后包含重复内容");
        }
        return normalized;
    }

    private String normalizeText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength);
        }
        return normalized;
    }

    private String normalizeKey(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private String serialize(Object value, String fieldName) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化" + fieldName + "失败", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }
}