package com.leo.careerforgeai.interview.evaluation;

import com.leo.careerforgeai.interview.domain.review.EvidenceConsistencyVerdict;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestionType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 定义并读取单评审基线与多角色Graph共享的固定面试评测集
 * @author: Miao Zheng
 * @date: 2026-08-30
 * @param schemaVersion 评测数据结构版本
 * @param evaluationSetVersion 固定评测集版本
 * @param cases 固定面试Case
 */
record InterviewArchitectureEvaluationDataset(
        String schemaVersion,
        String evaluationSetVersion,
        List<EvaluationCase> cases
) {

    InterviewArchitectureEvaluationDataset {
        if (!"interview-architecture-evaluation-v1".equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion不受支持");
        }
        if (!Set.of(
                "careerforge-interview-architecture-eval-v1",
                "careerforge-interview-architecture-eval-v2"
        ).contains(evaluationSetVersion)) {
            throw new IllegalArgumentException("evaluationSetVersion不受支持");
        }
        if (cases == null || cases.size() < 3 || cases.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("固定面试评测集至少需要3条Case");
        }
        cases = List.copyOf(cases);
        if (cases.stream().map(EvaluationCase::caseId).distinct().count() != cases.size()) {
            throw new IllegalArgumentException("caseId不能重复");
        }
        Set<InterviewQuestionType> questionTypes = EnumSet.noneOf(InterviewQuestionType.class);
        cases.forEach(evaluationCase -> questionTypes.add(evaluationCase.questionType()));
        if (!questionTypes.equals(EnumSet.allOf(InterviewQuestionType.class))) {
            throw new IllegalArgumentException("固定面试评测集必须覆盖全部问题类型");
        }
    }

    static InterviewArchitectureEvaluationDataset load() {
        return load("interview/evaluation/interview-architecture-cases-v1.json");
    }

    static InterviewArchitectureEvaluationDataset load(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("resourcePath不能为空");
        }
        InputStream resource = InterviewArchitectureEvaluationDataset.class.getClassLoader()
                .getResourceAsStream(resourcePath);
        if (resource == null) throw new IllegalStateException("固定面试评测集不存在：" + resourcePath);

        try (InputStream input = resource) {
            return JsonMapper.builder().build().readerFor(InterviewArchitectureEvaluationDataset.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(input);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("固定面试评测集解析或校验失败", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("固定面试评测集关闭失败", exception);
        }
    }
    /**
     * @program: CareerForge-AI
     * @description: 定义一条可供两种评审架构公平复用的固定面试输入和Gold Label
     * @author: Miao Zheng
     * @date: 2026-08-30
     * @param caseId Case唯一标识
     * @param questionType 问题类型
     * @param question 固定问题
     * @param answer 固定回答
     * @param evidenceByChunkId 冻结证据片段
     * @param targetSkills 问题应覆盖的目标技能
     * @param scoreDimensions Java指定的评分维度
     * @param scoringRubric Java指定的评分规则
     * @param expectedScoreRanges 各评分维度的Gold范围
     * @param requiredCoveredConcepts 评审应识别的已覆盖概念
     * @param requiredIssueConcepts 评审应识别的错误或缺失概念
     * @param expectedEvidenceVerdict Gold证据结论
     * @param expectedEvidenceReferenceIds Gold证据引用
     * @param expectedActionTopics 报告建议应覆盖的改进主题
     */
    record EvaluationCase(
            String caseId,
            InterviewQuestionType questionType,
            String question,
            String answer,
            Map<String, String> evidenceByChunkId,
            List<String> targetSkills,
            List<String> scoreDimensions,
            List<String> scoringRubric,
            Map<String, ScoreRange> expectedScoreRanges,
            List<String> requiredCoveredConcepts,
            List<String> requiredIssueConcepts,
            EvidenceConsistencyVerdict expectedEvidenceVerdict,
            List<String> expectedEvidenceReferenceIds,
            List<String> expectedActionTopics
    ) {

        EvaluationCase {
            requireText(caseId, "caseId", 64);
            if (!Pattern.matches("[A-Z0-9][A-Z0-9_-]{2,63}", caseId)) {
                throw new IllegalArgumentException("caseId格式不合法");
            }
            Objects.requireNonNull(questionType, "questionType不能为空");
            requireText(question, "question", 4_000);
            requireText(answer, "answer", 8_000);

            if (evidenceByChunkId == null || evidenceByChunkId.size() > 10) {
                throw new IllegalArgumentException("evidenceByChunkId数量不合法");
            }
            evidenceByChunkId.forEach((chunkId, content) -> {
                if (chunkId == null || !Pattern.matches("[0-9a-f]{64}", chunkId)) {
                    throw new IllegalArgumentException("证据chunkId必须是小写SHA-256");
                }
                requireText(content, "evidenceContent", 8_000);
            });
            evidenceByChunkId = Map.copyOf(evidenceByChunkId);

            targetSkills = requireUniqueTextList(targetSkills, "targetSkills", 1, 10);
            scoreDimensions = requireUniqueTextList(scoreDimensions, "scoreDimensions", 1, 10);
            scoringRubric = requireUniqueTextList(scoringRubric, "scoringRubric", 1, 20);
            requiredCoveredConcepts = requireUniqueTextList(requiredCoveredConcepts, "requiredCoveredConcepts", 1, 20);
            requiredIssueConcepts = requireUniqueTextList(requiredIssueConcepts, "requiredIssueConcepts", 1, 20);
            expectedActionTopics = requireUniqueTextList(expectedActionTopics, "expectedActionTopics", 1, 20);

            if (expectedScoreRanges == null || !expectedScoreRanges.keySet().equals(Set.copyOf(scoreDimensions))) {
                throw new IllegalArgumentException("expectedScoreRanges必须与scoreDimensions完全一致");
            }
            if (expectedScoreRanges.values().stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("expectedScoreRanges不能包含空值");
            }
            expectedScoreRanges = Map.copyOf(expectedScoreRanges);

            Objects.requireNonNull(expectedEvidenceVerdict, "expectedEvidenceVerdict不能为空");
            expectedEvidenceReferenceIds = requireUniqueTextList(
                    expectedEvidenceReferenceIds, "expectedEvidenceReferenceIds", 0, 10
            );
            if (!evidenceByChunkId.keySet().containsAll(expectedEvidenceReferenceIds)) {
                throw new IllegalArgumentException("Gold证据引用必须来自当前Case的证据白名单");
            }
            if (evidenceByChunkId.isEmpty() && (expectedEvidenceVerdict != EvidenceConsistencyVerdict.NOT_APPLICABLE
                    || !expectedEvidenceReferenceIds.isEmpty())) {
                throw new IllegalArgumentException("无证据Case必须是NOT_APPLICABLE且引用为空");
            }
            if (!evidenceByChunkId.isEmpty() && expectedEvidenceVerdict == EvidenceConsistencyVerdict.NOT_APPLICABLE) {
                throw new IllegalArgumentException("存在证据时Gold结论不能是NOT_APPLICABLE");
            }
            if (requiresReference(expectedEvidenceVerdict) && expectedEvidenceReferenceIds.isEmpty()) {
                throw new IllegalArgumentException("当前Gold证据结论必须包含引用");
            }
        }

        private static boolean requiresReference(EvidenceConsistencyVerdict verdict) {
            return verdict == EvidenceConsistencyVerdict.SUPPORTED
                    || verdict == EvidenceConsistencyVerdict.PARTIALLY_SUPPORTED
                    || verdict == EvidenceConsistencyVerdict.CONTRADICTED;
        }

        private static List<String> requireUniqueTextList(List<String> values, String field, int minimum, int maximum) {
            if (values == null || values.size() < minimum || values.size() > maximum) {
                throw new IllegalArgumentException(field + "数量不合法");
            }
            values.forEach(value -> requireText(value, field, 1_000));
            if (new HashSet<>(values).size() != values.size()) {
                throw new IllegalArgumentException(field + "不能包含重复项");
            }
            return List.copyOf(values);
        }

        private static void requireText(String value, String field, int maximumLength) {
            if (value == null || value.isBlank() || value.length() > maximumLength) {
                throw new IllegalArgumentException(field + "不能为空且长度不能超过" + maximumLength);
            }
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义人工Gold评分允许范围，避免把主观评分错误地约束为单一数值
     * @author: Miao Zheng
     * @date: 2026-08-30
     * @param minimum 最低允许分数
     * @param maximum 最高允许分数
     */
    record ScoreRange(int minimum, int maximum) {

        ScoreRange {
            if (minimum < 0 || maximum > 5 || minimum > maximum) {
                throw new IllegalArgumentException("Gold评分范围必须位于0至5且minimum不能大于maximum");
            }
        }

        boolean contains(int score) {
            return score >= minimum && score <= maximum;
        }
    }
}