package com.leo.careerforgeai.interview.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存由Java根据冻结输入生成的确定性面试蓝图
 * @author: Miao Zheng
 * @date: 2026-08-28
 * @param schemaVersion 蓝图结构版本
 * @param inputSnapshotHash 蓝图绑定的输入快照Hash
 * @param mode 面试模式
 * @param budgetPolicy 服务端预算
 * @param questionPlans 按顺序排列的非追问题槽
 **/
public record InterviewBlueprint(
        int schemaVersion,
        String inputSnapshotHash,
        InterviewMode mode,
        InterviewBudgetPolicy budgetPolicy,
        List<QuestionPlan> questionPlans
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_PLANNED_QUESTIONS = 20;
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public InterviewBlueprint {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的蓝图schemaVersion: " + schemaVersion);
        }
        if (inputSnapshotHash == null || !SHA256_PATTERN.matcher(inputSnapshotHash).matches()) {
            throw new IllegalArgumentException("inputSnapshotHash必须是64位小写SHA-256");
        }
        Objects.requireNonNull(mode, "mode不能为空");
        Objects.requireNonNull(budgetPolicy, "budgetPolicy不能为空");
        Objects.requireNonNull(questionPlans, "questionPlans不能为空");

        questionPlans = List.copyOf(questionPlans);
        if (questionPlans.isEmpty() || questionPlans.size() > MAX_PLANNED_QUESTIONS) {
            throw new IllegalArgumentException("questionPlans数量必须在1到20之间");
        }
        if (questionPlans.size() != budgetPolicy.maxQuestions()) {
            throw new IllegalArgumentException("questionPlans数量必须等于maxQuestions");
        }

        for (int index = 0; index < questionPlans.size(); index++) {
            QuestionPlan plan = Objects.requireNonNull(questionPlans.get(index), "questionPlan不能为空");
            if (plan.sequence() != index + 1) {
                throw new IllegalArgumentException("questionPlan.sequence必须从1开始连续递增");
            }
        }
    }

    public QuestionPlan questionAt(int sequence) {
        if (sequence < 1 || sequence > questionPlans.size()) {
            throw new IllegalArgumentException("问题序号超出蓝图范围");
        }
        return questionPlans.get(sequence - 1);
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义蓝图中的单个非追问题槽
     * @author: Miao Zheng
     * @date: 2026-08-28
     * @param sequence 从1开始的问题顺序
     * @param questionType Java指定的问题类型
     * @param difficulty Java指定的难度等级
     * @param targetSkills 本题必须覆盖的技能主题
     * @param currentRoundGoal 本题确定性考察目标
     * @param evidencePreferred 是否优先使用允许的个人证据
     **/
    public record QuestionPlan(
            int sequence,
            InterviewQuestionType questionType,
            int difficulty,
            List<String> targetSkills,
            String currentRoundGoal,
            boolean evidencePreferred
    ) {

        public QuestionPlan {
            if (sequence < 1) throw new IllegalArgumentException("sequence必须从1开始");
            Objects.requireNonNull(questionType, "questionType不能为空");
            if (difficulty < 1 || difficulty > 5) {
                throw new IllegalArgumentException("difficulty必须在1到5之间");
            }

            targetSkills = normalizeSkills(targetSkills);
            if (currentRoundGoal == null || currentRoundGoal.isBlank()
                    || currentRoundGoal.length() > 1_000) {
                throw new IllegalArgumentException("currentRoundGoal不能为空且长度不能超过1000");
            }
            currentRoundGoal = currentRoundGoal.strip();

            if (evidencePreferred && questionType != InterviewQuestionType.PROJECT_DEEP_DIVE) {
                throw new IllegalArgumentException("只有项目深挖题可以优先使用个人证据");
            }
        }

        private static List<String> normalizeSkills(List<String> skills) {
            if (skills == null || skills.isEmpty() || skills.size() > 5) {
                throw new IllegalArgumentException("targetSkills数量必须在1到5之间");
            }

            List<String> normalized = skills.stream()
                    .map(skill -> {
                        if (skill == null || skill.isBlank()) {
                            throw new IllegalArgumentException("targetSkills不能包含空值");
                        }
                        String value = skill.strip();
                        if (value.length() > 100) {
                            throw new IllegalArgumentException("targetSkills元素长度不能超过100");
                        }
                        return value;
                    })
                    .toList();

            if (new HashSet<>(normalized).size() != normalized.size()) {
                throw new IllegalArgumentException("targetSkills不能重复");
            }
            return normalized;
        }
    }
}