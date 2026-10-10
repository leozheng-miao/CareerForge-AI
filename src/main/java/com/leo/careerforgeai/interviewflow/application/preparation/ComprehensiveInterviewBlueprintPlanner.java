package com.leo.careerforgeai.interviewflow.application.preparation;

import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.KnowledgeQuestionCandidate;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.SourceReference;
import com.leo.careerforgeai.interviewflow.application.preparation.ComprehensivePreparationCoordinator.PreparationSignals;
import com.leo.careerforgeai.interviewflow.application.preparation.ProfileEvidenceAnalyzer.ExperienceSignal;
import com.leo.careerforgeai.interviewflow.domain.topic.InterviewTopic;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 以确定性覆盖规则生成六个互补主题，画像主张只用作待验证线索。 */
@Component
public final class ComprehensiveInterviewBlueprintPlanner {
    private final ComprehensiveInterviewPolicyPack policyPack;

    public ComprehensiveInterviewBlueprintPlanner() {
        this(new ComprehensiveInterviewPolicyPack());
    }

    ComprehensiveInterviewBlueprintPlanner(ComprehensiveInterviewPolicyPack policyPack) {
        this.policyPack = Objects.requireNonNull(policyPack);
    }

    public Blueprint plan(UUID interviewId, PreparationSignals signals) {
        return plan(interviewId, 1, signals);
    }

    public Blueprint plan(UUID interviewId, long planVersion, PreparationSignals signals) {
        Objects.requireNonNull(interviewId);
        Objects.requireNonNull(signals);
        if (planVersion < 1) throw new IllegalArgumentException("planVersion必须大于等于1");
        List<PlannedTheme> themes = new ArrayList<>();
        List<ExperienceSignal> experiences = signals.profile().experiences();
        int experienceCount = Math.min(3, experiences.size());
        for (int index = 0; index < experienceCount; index++) {
            ExperienceSignal experience = experiences.get(index % experiences.size());
            String code = "EXPERIENCE_" + (index + 1);
            String objective = "验证确认画像中的“" + safeLabel(experience.label()) + "”"
                    + experience.category() + "经历及其与岗位能力的关联";
            String question = "你在确认画像中提到“" + safeLabel(experience.label())
                    + "”。请说明你亲自负责的目标、关键决策、结果证据，以及如果重新实现会如何改进。";
            themes.add(theme(interviewId, planVersion, themes.size() + 1, code, objective, question,
                    sourceFor(signals.knowledgeCandidates(), index),
                    "confirmed-profile:" + experience.category() + ":" + index));
        }
        int coreIndex = 0;
        while (themes.size() < ComprehensiveInterviewPolicyPack.REQUIRED_THEME_COUNT) {
            ComprehensiveInterviewPolicyPack.CoreTheme core = policyPack.coreThemes().get(coreIndex++);
            String capability = signals.jd().capabilitySignals().get(
                    Math.min(coreIndex - 1, signals.jd().capabilitySignals().size() - 1));
            String objective = core.objective() + "；JD信号：" + capability;
            String question = core.backupQuestion() + " 当前难度关注："
                    + signals.jd().levelSignal() + "。";
            themes.add(theme(interviewId, planVersion, themes.size() + 1, core.code(), objective, question,
                    sourceFor(signals.knowledgeCandidates(), coreIndex - 1, core.sources()),
                    "jd-signal:" + capability));
        }
        validate(themes);
        return new Blueprint(ComprehensiveInterviewPolicyPack.VERSION, themes);
    }

    private static PlannedTheme theme(UUID interviewId, long planVersion, int sequenceNo,
            String code, String objective,
            String question, List<SourceReference> sources, String rationale) {
        UUID topicId = UUID.nameUUIDFromBytes(("comprehensive-topic:"
                + interviewId + ":plan:" + planVersion + ":" + code)
                .getBytes(StandardCharsets.UTF_8));
        return new PlannedTheme(code, InterviewTopic.planned(topicId, sequenceNo, objective),
                objective, question, sources, rationale);
    }

    private static void validate(List<PlannedTheme> rawThemes) {
        if (rawThemes.size() != ComprehensiveInterviewPolicyPack.REQUIRED_THEME_COUNT) {
            throw new IllegalStateException("综合面试必须包含6个主题");
        }
        Set<String> codes = new HashSet<>();
        Set<String> objectives = new HashSet<>();
        for (PlannedTheme theme : rawThemes) {
            if (!codes.add(theme.code()) || !objectives.add(theme.objective())) {
                throw new IllegalStateException("综合面试主题必须互补且不能重复");
            }
        }
    }

    private static List<SourceReference> sourceFor(List<KnowledgeQuestionCandidate> candidates,
            int index) {
        return sourceFor(candidates, index, List.of(new SourceReference(
                "careerforge-confirmed-input", "confirmed-profile", "experience-" + index)));
    }

    private static List<SourceReference> sourceFor(List<KnowledgeQuestionCandidate> candidates,
            int index, List<SourceReference> fallback) {
        if (!candidates.isEmpty()) {
            return candidates.get(index % candidates.size()).sourceReferences();
        }
        return fallback;
    }

    private static String safeLabel(String value) {
        String normalized = value == null ? "相关经历" : value.strip();
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    public record Blueprint(String policyVersion, List<PlannedTheme> themes) {
        public Blueprint {
            themes = List.copyOf(themes);
        }

        public List<InterviewTopic> topics() {
            return themes.stream().map(PlannedTheme::topic).toList();
        }
    }

    public record PlannedTheme(String code, InterviewTopic topic, String objective,
            String question, List<SourceReference> sources, String coverageRationale) {
        public PlannedTheme {
            sources = List.copyOf(sources);
        }

    }
}
