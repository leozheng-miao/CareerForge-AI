package com.leo.careerforgeai.interview.application.blueprint;

import com.leo.careerforgeai.interview.domain.round.InterviewBlueprint;
import com.leo.careerforgeai.interview.domain.session.InterviewMode;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestionType;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 根据冻结Session、岗位主题和Gap主题生成可重复的确定性面试蓝图
 * @author: Miao Zheng
 * @date: 2026-08-28
 **/
@Component
public class InterviewBlueprintPlanner {

    public InterviewBlueprint plan(
            MockInterviewSession session,
            List<String> targetSkills,
            List<String> gapSkills,
            boolean projectEvidenceAvailable
    ) {
        Objects.requireNonNull(session, "session不能为空");

        if (session.budgetPolicy().maxQuestions() > InterviewBlueprint.MAX_PLANNED_QUESTIONS) {
            throw new IllegalArgumentException("maxQuestions不能超过20");
        }

        List<String> normalizedTargets = normalizeSkills(targetSkills, "targetSkills", true);
        List<String> normalizedGaps = normalizeSkills(
                gapSkills,
                "gapSkills",
                session.mode() == InterviewMode.GAP_DRILL
        );
        List<String> focusSkills = session.mode() == InterviewMode.GAP_DRILL
                ? normalizedGaps
                : merge(normalizedTargets, normalizedGaps);

        List<InterviewQuestionType> typeSchedule = projectEvidenceAvailable
                ? List.of(
                        InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                        InterviewQuestionType.PROJECT_DEEP_DIVE,
                        InterviewQuestionType.SYSTEM_DESIGN
                )
                : List.of(
                        InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                        InterviewQuestionType.SYSTEM_DESIGN
                );

        List<InterviewBlueprint.QuestionPlan> questionPlans = new ArrayList<>();
        for (int index = 0; index < session.budgetPolicy().maxQuestions(); index++) {
            InterviewQuestionType type = typeSchedule.get(index % typeSchedule.size());
            String skill = focusSkills.get(index % focusSkills.size());
            int difficulty = Math.min(5, 2 + index / 2);
            boolean evidencePreferred =
                    type == InterviewQuestionType.PROJECT_DEEP_DIVE && projectEvidenceAvailable;

            questionPlans.add(new InterviewBlueprint.QuestionPlan(
                    index + 1,
                    type,
                    difficulty,
                    List.of(skill),
                    goal(type, skill),
                    evidencePreferred
            ));
        }

        return new InterviewBlueprint(
                InterviewBlueprint.CURRENT_SCHEMA_VERSION,
                session.inputSnapshotHash(),
                session.mode(),
                session.budgetPolicy(),
                questionPlans
        );
    }

    private List<String> normalizeSkills(
            List<String> skills,
            String fieldName,
            boolean required
    ) {
        Objects.requireNonNull(skills, fieldName + "不能为空");
        Map<String, String> normalized = new LinkedHashMap<>();

        for (String skill : skills) {
            if (skill == null || skill.isBlank()) {
                throw new IllegalArgumentException(fieldName + "不能包含空值");
            }
            String value = skill.strip();
            if (value.length() > 100) {
                throw new IllegalArgumentException(fieldName + "元素长度不能超过100");
            }
            normalized.putIfAbsent(value.toLowerCase(Locale.ROOT), value);
        }

        if (required && normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return List.copyOf(normalized.values());
    }

    private List<String> merge(List<String> first, List<String> second) {
        Map<String, String> merged = new LinkedHashMap<>();
        first.forEach(value -> merged.putIfAbsent(value.toLowerCase(Locale.ROOT), value));
        second.forEach(value -> merged.putIfAbsent(value.toLowerCase(Locale.ROOT), value));
        return List.copyOf(merged.values());
    }

    private String goal(InterviewQuestionType type, String skill) {
        return switch (type) {
            case TECHNICAL_KNOWLEDGE ->
                    "验证候选人对“" + skill + "”的技术原理、适用边界和失败场景理解。";
            case PROJECT_DEEP_DIVE ->
                    "验证候选人能否使用冻结项目证据解释“" + skill + "”的真实决策、实现和验证结果。";
            case SYSTEM_DESIGN ->
                    "验证候选人能否围绕“" + skill + "”完成约束、方案、权衡和故障处理设计。";
        };
    }
}