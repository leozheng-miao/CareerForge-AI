package com.leo.careerforgeai.interview.application.report;

import com.leo.careerforgeai.interview.application.model.contract.InterviewReportDraft;
import com.leo.careerforgeai.interview.application.model.contract.InterviewReportInput;
import com.leo.careerforgeai.interview.application.model.contract.InterviewReportSuggestionDraft;
import com.leo.careerforgeai.interview.domain.EvidenceConsistencyVerdict;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 根据Java业务事实过滤报告优势、Memory候选和训练计划调整建议
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
@Component
public class InterviewReportMemoryCandidatePolicy {

    public List<String> deriveAllowedStrengths(
            Map<String, Integer> dimensionScores,
            EvidenceConsistencyVerdict evidenceVerdict,
            List<String> coveredPoints
    ) {
        Objects.requireNonNull(coveredPoints, "coveredPoints不能为空");
        if (!eligible(dimensionScores, evidenceVerdict)) return List.of();

        ArrayList<String> strengths = new ArrayList<>();
        HashSet<String> normalized = new HashSet<>();
        for (String value : coveredPoints) {
            if (value == null || value.isBlank()) continue;
            String strength = value.strip().replaceAll("\\s+", " ");
            if (strength.length() > 500 || !normalized.add(strength.toLowerCase(Locale.ROOT))) continue;
            strengths.add(strength);
            if (strengths.size() == 20) break;
        }
        return List.copyOf(strengths);
    }

    public List<InterviewReportInput.AllowedMemoryCandidate> deriveAllowedCandidates(
            List<String> targetSkills,
            Map<String, Integer> dimensionScores,
            EvidenceConsistencyVerdict evidenceVerdict,
            String answerText
    ) {
        Objects.requireNonNull(targetSkills, "targetSkills不能为空");
        if (answerText == null || answerText.isBlank()) throw new IllegalArgumentException("answerText不能为空");
        if (!eligible(dimensionScores, evidenceVerdict)) return List.of();

        String normalizedAnswer = answerText.strip().replaceAll("\\s+", " ");
        Set<String> normalizedSkills = new HashSet<>();
        ArrayList<InterviewReportInput.AllowedMemoryCandidate> candidates = new ArrayList<>();

        for (String value : targetSkills) {
            if (value == null || value.isBlank()) continue;
            String skill = value.strip().replaceAll("\\s+", " ");
            if (skill.length() > 128 || !normalizedSkills.add(skill.toLowerCase(Locale.ROOT))) continue;

            String prefix = "面试回答中针对“" + skill + "”的原文摘录：";
            String content = prefix + bounded(normalizedAnswer, 1_000 - prefix.length());
            candidates.add(new InterviewReportInput.AllowedMemoryCandidate(skill, content));
            if (candidates.size() == 10) break;
        }
        return List.copyOf(candidates);
    }

    public InterviewReportDraft filter(InterviewReportInput input, InterviewReportDraft draft) {
        Objects.requireNonNull(input, "input不能为空");
        Objects.requireNonNull(draft, "draft不能为空");

        Set<String> allowedStrengths = Set.copyOf(input.allowedStrengths());
        List<String> filteredStrengths = draft.strengths().stream()
                .filter(allowedStrengths::contains)
                .toList();

        Set<InterviewReportSuggestionDraft.MemoryCandidate> allowedMemoryCandidates = new HashSet<>();
        for (InterviewReportInput.AllowedMemoryCandidate candidate : input.allowedMemoryCandidates()) {
            allowedMemoryCandidates.add(new InterviewReportSuggestionDraft.MemoryCandidate(
                    candidate.skillName(), candidate.content()
            ));
        }

        List<InterviewReportSuggestionDraft.MemoryCandidate> filteredMemoryCandidates =
                draft.proposedMemoryCandidates().stream()
                        .filter(allowedMemoryCandidates::contains)
                        .toList();
        List<InterviewReportSuggestionDraft.TrainingPlanAdjustment> filteredTrainingAdjustments =
                input.trainingPlanAdjustmentAllowed()
                        ? draft.proposedTrainingPlanAdjustments()
                        : List.of();

        if (filteredStrengths.equals(draft.strengths())
                && filteredMemoryCandidates.equals(draft.proposedMemoryCandidates())
                && filteredTrainingAdjustments.equals(draft.proposedTrainingPlanAdjustments())) {
            return draft;
        }

        return new InterviewReportDraft(
                filteredStrengths,
                draft.technicalGaps(),
                draft.evidenceExpressionRisks(),
                draft.improvementActions(),
                filteredMemoryCandidates,
                filteredTrainingAdjustments
        );
    }

    private boolean eligible(
            Map<String, Integer> dimensionScores,
            EvidenceConsistencyVerdict evidenceVerdict
    ) {
        Objects.requireNonNull(dimensionScores, "dimensionScores不能为空");
        Objects.requireNonNull(evidenceVerdict, "evidenceVerdict不能为空");
        if (dimensionScores.isEmpty() || dimensionScores.values().stream().anyMatch(
                score -> score == null || score < 3
        )) {
            return false;
        }
        return evidenceVerdict == EvidenceConsistencyVerdict.SUPPORTED
                || evidenceVerdict == EvidenceConsistencyVerdict.NOT_APPLICABLE;
    }

    private String bounded(String value, int maximumLength) {
        if (maximumLength < 1) throw new IllegalArgumentException("maximumLength必须大于0");
        if (value.length() <= maximumLength) return value;
        int end = maximumLength - 1;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + "…";
    }
}