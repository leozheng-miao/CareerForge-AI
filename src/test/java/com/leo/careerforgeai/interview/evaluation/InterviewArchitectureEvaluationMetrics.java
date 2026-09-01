package com.leo.careerforgeai.interview.evaluation;

import com.leo.careerforgeai.interview.domain.review.EvidenceConsistencyVerdict;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 基于固定Gold Label计算单评审基线与多角色Graph的质量、可靠性和成本指标
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
class InterviewArchitectureEvaluationMetrics {

    static Metrics evaluate(InterviewArchitectureEvaluationDataset dataset, EvaluationRun run) {
        Objects.requireNonNull(dataset, "dataset不能为空");
        Objects.requireNonNull(run, "run不能为空");

        Map<String, InterviewArchitectureEvaluationDataset.EvaluationCase> goldByCaseId = new LinkedHashMap<>();
        dataset.cases().forEach(evaluationCase -> goldByCaseId.put(evaluationCase.caseId(), evaluationCase));
        if (!run.caseIds().equals(goldByCaseId.keySet())) {
            throw new IllegalArgumentException("评测运行必须与固定评测集包含完全相同的caseId");
        }

        long targetSkillHits = 0;
        long targetSkillTotal = 0;
        long duplicateQuestions = 0;
        long questions = 0;
        long scoreRangeHits = 0;
        long scoreRangeTotal = 0;
        long coveredConceptHits = 0;
        long coveredConceptTotal = 0;
        long issueConceptHits = 0;
        long issueConceptTotal = 0;
        long verdictHits = 0;
        long evidenceReferenceHits = 0;
        long predictedEvidenceReferences = 0;
        long expectedEvidenceReferences = 0;
        long legalEvidenceReferences = 0;
        long actionTopicHits = 0;
        long actionTopicTotal = 0;
        long confirmedActions = 0;
        long proposedActions = 0;
        long hitlResumeSuccesses = 0;
        long modelCalls = 0;
        long totalTokens = 0;
        long duplicateModelSideEffects = 0;
        long stateRegressions = 0;
        long crossOwnerLeaks = 0;
        int succeeded = 0;
        List<Long> durations = new ArrayList<>();

        for (CaseObservation observation : run.observations()) {
            InterviewArchitectureEvaluationDataset.EvaluationCase gold = goldByCaseId.get(observation.caseId());
            validateAdjudication(gold, observation);

            targetSkillTotal += gold.targetSkills().size();
            scoreRangeTotal += gold.scoreDimensions().size();
            coveredConceptTotal += gold.requiredCoveredConcepts().size();
            issueConceptTotal += gold.requiredIssueConcepts().size();
            expectedEvidenceReferences += gold.expectedEvidenceReferenceIds().size();
            actionTopicTotal += gold.expectedActionTopics().size();

            questions += observation.questionCount();
            duplicateQuestions += observation.duplicateQuestionCount();
            confirmedActions += observation.confirmedActionCount();
            proposedActions += observation.proposedActionCount();
            modelCalls += observation.modelCallCount();
            totalTokens += observation.totalTokens();
            duplicateModelSideEffects += observation.duplicateModelSideEffectCount();
            stateRegressions += observation.stateRegressionCount();
            crossOwnerLeaks += observation.crossOwnerLeakCount();
            durations.add(observation.durationMs());

            if (!observation.successful()) continue;
            succeeded++;
            targetSkillHits += intersectionSize(observation.adjudicatedTargetSkills(), gold.targetSkills());
            coveredConceptHits += intersectionSize(observation.adjudicatedCoveredConcepts(), gold.requiredCoveredConcepts());
            issueConceptHits += intersectionSize(observation.adjudicatedIssueConcepts(), gold.requiredIssueConcepts());
            actionTopicHits += intersectionSize(observation.adjudicatedActionTopics(), gold.expectedActionTopics());
            if (observation.evidenceVerdict() == gold.expectedEvidenceVerdict()) verdictHits++;
            if (observation.hitlResumeSucceeded()) hitlResumeSuccesses++;

            for (String dimension : gold.scoreDimensions()) {
                Integer score = observation.dimensionScores().get(dimension);
                if (score != null && gold.expectedScoreRanges().get(dimension).contains(score)) scoreRangeHits++;
            }

            Set<String> expectedReferences = Set.copyOf(gold.expectedEvidenceReferenceIds());
            Set<String> allowedReferences = gold.evidenceByChunkId().keySet();
            for (String referenceId : observation.evidenceReferenceIds()) {
                predictedEvidenceReferences++;
                if (expectedReferences.contains(referenceId)) evidenceReferenceHits++;
                if (allowedReferences.contains(referenceId)) legalEvidenceReferences++;
            }
        }

        int observationCount = run.observations().size();
        return new Metrics(
                run.architecture(),
                observationCount,
                succeeded,
                observationCount - succeeded,
                percentage(succeeded, observationCount),
                percentage(targetSkillHits, targetSkillTotal),
                percentage(duplicateQuestions, questions),
                percentage(scoreRangeHits, scoreRangeTotal),
                percentage(coveredConceptHits, coveredConceptTotal),
                percentage(issueConceptHits, issueConceptTotal),
                percentage(verdictHits, observationCount),
                percentage(evidenceReferenceHits, predictedEvidenceReferences),
                percentage(evidenceReferenceHits, expectedEvidenceReferences),
                percentage(legalEvidenceReferences, predictedEvidenceReferences),
                percentage(actionTopicHits, actionTopicTotal),
                percentage(confirmedActions, proposedActions),
                percentage(hitlResumeSuccesses, observationCount),
                modelCalls,
                totalTokens,
                percentile(durations, 0.50),
                percentile(durations, 0.95),
                duplicateModelSideEffects,
                stateRegressions,
                crossOwnerLeaks
        );
    }

    static Comparison compare(
            InterviewArchitectureEvaluationDataset dataset,
            EvaluationRun baseline,
            EvaluationRun multiRoleGraph
    ) {
        if (baseline.architecture() != Architecture.SINGLE_REVIEW_BASELINE) {
            throw new IllegalArgumentException("baseline必须是SINGLE_REVIEW_BASELINE");
        }
        if (multiRoleGraph.architecture() != Architecture.MULTI_ROLE_GRAPH) {
            throw new IllegalArgumentException("multiRoleGraph必须是MULTI_ROLE_GRAPH");
        }
        if (!baseline.caseIds().equals(multiRoleGraph.caseIds())) {
            throw new IllegalArgumentException("两种架构必须使用完全相同的固定Case");
        }
        return new Comparison(evaluate(dataset, baseline), evaluate(dataset, multiRoleGraph));
    }

    private static void validateAdjudication(
            InterviewArchitectureEvaluationDataset.EvaluationCase gold,
            CaseObservation observation
    ) {
        if (!observation.successful()) {
            if (!observation.dimensionScores().isEmpty() || observation.evidenceVerdict() != null
                    || !observation.evidenceReferenceIds().isEmpty()) {
                throw new IllegalArgumentException("失败观察不能携带完整模型结论");
            }
            return;
        }
        if (!observation.dimensionScores().keySet().equals(Set.copyOf(gold.scoreDimensions()))) {
            throw new IllegalArgumentException("成功观察的评分维度必须与固定Rubric完全一致");
        }
        requireGoldLabels(observation.adjudicatedTargetSkills(), gold.targetSkills(), "adjudicatedTargetSkills");
        requireGoldLabels(observation.adjudicatedCoveredConcepts(), gold.requiredCoveredConcepts(), "adjudicatedCoveredConcepts");
        requireGoldLabels(observation.adjudicatedIssueConcepts(), gold.requiredIssueConcepts(), "adjudicatedIssueConcepts");
        requireGoldLabels(observation.adjudicatedActionTopics(), gold.expectedActionTopics(), "adjudicatedActionTopics");
    }

    private static void requireGoldLabels(Set<String> actual, List<String> allowed, String field) {
        if (!Set.copyOf(allowed).containsAll(actual)) {
            throw new IllegalArgumentException(field + "包含固定Gold之外的标签");
        }
    }

    private static long intersectionSize(Set<String> actual, List<String> expected) {
        return expected.stream().filter(actual::contains).count();
    }

    private static double percentage(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : numerator * 100.0 / denominator;
    }

    private static long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = values.stream().sorted().toList();
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
        return sorted.get(index);
    }

    /**
     * @program: CareerForge-AI
     * @description: 区分参与固定对照的单评审基线和正式多角色Graph
     * @author: Miao Zheng
     * @date: 2026-08-30
     */
    enum Architecture {
        SINGLE_REVIEW_BASELINE,
        MULTI_ROLE_GRAPH
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义一种评审架构在完整固定Case集合上的运行结果
     * @author: Miao Zheng
     * @date: 2026-08-30
     * @param architecture 被评测架构
     * @param observations 每个固定Case的观察结果
     */
    record EvaluationRun(Architecture architecture, List<CaseObservation> observations) {

        EvaluationRun {
            Objects.requireNonNull(architecture, "architecture不能为空");
            if (observations == null || observations.isEmpty() || observations.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("observations不能为空");
            }
            observations = List.copyOf(observations);
            if (observations.stream().map(CaseObservation::caseId).distinct().count() != observations.size()) {
                throw new IllegalArgumentException("同一次评测运行不能重复caseId");
            }
        }

        Set<String> caseIds() {
            Set<String> result = new HashSet<>();
            observations.forEach(observation -> result.add(observation.caseId()));
            return Set.copyOf(result);
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存单个Case经人工标签裁决后的质量结果及客观可靠性成本数据
     * @author: Miao Zheng
     * @date: 2026-08-30
     * @param caseId 固定Case标识
     * @param successful 本次架构是否完整产出结果
     * @param adjudicatedTargetSkills 人工确认问题实际覆盖的Gold技能
     * @param questionCount 问题总数
     * @param duplicateQuestionCount 重复问题数
     * @param dimensionScores 各固定维度评分
     * @param adjudicatedCoveredConcepts 人工确认已识别的Gold覆盖点
     * @param adjudicatedIssueConcepts 人工确认已识别的Gold错误或缺失点
     * @param evidenceVerdict 模型证据结论
     * @param evidenceReferenceIds 模型证据引用
     * @param adjudicatedActionTopics 人工确认建议覆盖的Gold行动主题
     * @param confirmedActionCount 用户确认的建议数
     * @param proposedActionCount 提交用户确认的建议数
     * @param hitlResumeSucceeded HITL是否成功恢复
     * @param duplicateModelSideEffectCount 重复模型副作用数
     * @param stateRegressionCount 状态倒退数
     * @param crossOwnerLeakCount 跨owner泄漏数
     * @param modelCallCount 模型调用数
     * @param totalTokens Token总量
     * @param durationMs 端到端耗时
     */
    record CaseObservation(
            String caseId,
            boolean successful,
            Set<String> adjudicatedTargetSkills,
            int questionCount,
            int duplicateQuestionCount,
            Map<String, Integer> dimensionScores,
            Set<String> adjudicatedCoveredConcepts,
            Set<String> adjudicatedIssueConcepts,
            EvidenceConsistencyVerdict evidenceVerdict,
            List<String> evidenceReferenceIds,
            Set<String> adjudicatedActionTopics,
            int confirmedActionCount,
            int proposedActionCount,
            boolean hitlResumeSucceeded,
            int duplicateModelSideEffectCount,
            int stateRegressionCount,
            int crossOwnerLeakCount,
            int modelCallCount,
            long totalTokens,
            long durationMs
    ) {

        CaseObservation {
            if (caseId == null || caseId.isBlank()) throw new IllegalArgumentException("caseId不能为空");
            adjudicatedTargetSkills = copySet(adjudicatedTargetSkills, "adjudicatedTargetSkills");
            adjudicatedCoveredConcepts = copySet(adjudicatedCoveredConcepts, "adjudicatedCoveredConcepts");
            adjudicatedIssueConcepts = copySet(adjudicatedIssueConcepts, "adjudicatedIssueConcepts");
            adjudicatedActionTopics = copySet(adjudicatedActionTopics, "adjudicatedActionTopics");
            if (dimensionScores == null || dimensionScores.values().stream().anyMatch(
                    score -> score == null || score < 0 || score > 5
            )) {
                throw new IllegalArgumentException("dimensionScores必须存在且分数位于0至5");
            }
            dimensionScores = Map.copyOf(dimensionScores);
            if (evidenceReferenceIds == null || evidenceReferenceIds.stream().anyMatch(
                    referenceId -> referenceId == null || referenceId.isBlank()
            )) {
                throw new IllegalArgumentException("evidenceReferenceIds不能包含空值");
            }
            evidenceReferenceIds = List.copyOf(evidenceReferenceIds);
            if (new HashSet<>(evidenceReferenceIds).size() != evidenceReferenceIds.size()) {
                throw new IllegalArgumentException("evidenceReferenceIds不能重复");
            }
            requireNonNegative(questionCount, "questionCount");
            requireNonNegative(duplicateQuestionCount, "duplicateQuestionCount");
            if (duplicateQuestionCount > questionCount) {
                throw new IllegalArgumentException("duplicateQuestionCount不能大于questionCount");
            }
            requireNonNegative(confirmedActionCount, "confirmedActionCount");
            requireNonNegative(proposedActionCount, "proposedActionCount");
            if (confirmedActionCount > proposedActionCount) {
                throw new IllegalArgumentException("confirmedActionCount不能大于proposedActionCount");
            }
            requireNonNegative(duplicateModelSideEffectCount, "duplicateModelSideEffectCount");
            requireNonNegative(stateRegressionCount, "stateRegressionCount");
            requireNonNegative(crossOwnerLeakCount, "crossOwnerLeakCount");
            requireNonNegative(modelCallCount, "modelCallCount");
            if (totalTokens < 0 || durationMs < 0) {
                throw new IllegalArgumentException("Token和耗时不能小于0");
            }
            if (successful && evidenceVerdict == null) {
                throw new IllegalArgumentException("成功观察必须包含evidenceVerdict");
            }
        }

        private static Set<String> copySet(Set<String> values, String field) {
            if (values == null || values.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException(field + "不能包含空值");
            }
            return Set.copyOf(values);
        }

        private static void requireNonNegative(int value, String field) {
            if (value < 0) throw new IllegalArgumentException(field + "不能小于0");
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 汇总一种架构的质量、可靠性和成本指标
     * @author: Miao Zheng
     * @date: 2026-08-30
     * @param architecture 被评测架构
     * @param observationCount 固定观察数量
     * @param succeededCount 成功数量
     * @param failedCount 失败数量
     * @param successRatePercent 成功率
     * @param targetSkillCoveragePercent 目标技能覆盖率
     * @param duplicateQuestionRatePercent 重复问题率
     * @param scoreWithinGoldRangePercent 评分落入Gold范围的比例
     * @param coveredConceptRecallPercent 已覆盖概念召回率
     * @param issueConceptRecallPercent 错误或缺失概念召回率
     * @param evidenceVerdictAccuracyPercent 证据分类准确率
     * @param evidenceReferencePrecisionPercent 证据引用precision
     * @param evidenceReferenceRecallPercent 证据引用recall
     * @param legalEvidenceReferenceRatePercent 合法引用率
     * @param actionTopicRecallPercent 可执行建议主题召回率
     * @param userConfirmationRatePercent 用户确认率
     * @param hitlResumeSuccessRatePercent HITL恢复成功率
     * @param totalModelCalls 模型调用总数
     * @param totalTokens Token总数
     * @param p50DurationMs 端到端p50耗时
     * @param p95DurationMs 端到端p95耗时
     * @param duplicateModelSideEffectCount 重复模型副作用总数
     * @param stateRegressionCount 状态倒退总数
     * @param crossOwnerLeakCount 跨owner泄漏总数
     */
    record Metrics(
            Architecture architecture,
            int observationCount,
            int succeededCount,
            int failedCount,
            double successRatePercent,
            double targetSkillCoveragePercent,
            double duplicateQuestionRatePercent,
            double scoreWithinGoldRangePercent,
            double coveredConceptRecallPercent,
            double issueConceptRecallPercent,
            double evidenceVerdictAccuracyPercent,
            double evidenceReferencePrecisionPercent,
            double evidenceReferenceRecallPercent,
            double legalEvidenceReferenceRatePercent,
            double actionTopicRecallPercent,
            double userConfirmationRatePercent,
            double hitlResumeSuccessRatePercent,
            long totalModelCalls,
            long totalTokens,
            long p50DurationMs,
            long p95DurationMs,
            long duplicateModelSideEffectCount,
            long stateRegressionCount,
            long crossOwnerLeakCount
    ) {
    }

    /**
     * @program: CareerForge-AI
     * @description: 并列保存两种架构指标，不预先计算或宣称胜者
     * @author: Miao Zheng
     * @date: 2026-08-30
     * @param baseline 单评审基线指标
     * @param multiRoleGraph 多角色Graph指标
     */
    record Comparison(Metrics baseline, Metrics multiRoleGraph) {
    }
}