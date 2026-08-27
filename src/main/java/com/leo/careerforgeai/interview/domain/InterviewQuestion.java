package com.leo.careerforgeai.interview.domain;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存经Java契约校验后持久化的不可变模拟面试问题
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param questionId 问题UUID
 * @param interviewId 所属面试UUID
 * @param roundId 所属回合UUID
 * @param ownerId 所属用户
 * @param parentQuestionId 追问对应的父问题UUID
 * @param questionType 问题类型
 * @param questionText 问题正文
 * @param difficulty 1至5级难度
 * @param targetSkills 目标技能
 * @param evaluationPoints 评价要点
 * @param followUpAllowed 当前问题是否允许追问
 * @param followUp 当前问题是否为追问
 * @param evidenceReferenceIds 允许问题引用的证据片段ID
 * @param modelRequestId 生成问题的模型请求ID
 * @param promptVersion Prompt版本
 * @param contentHash 问题内容的小写SHA-256
 * @param createdAt 创建时间
 **/
public record InterviewQuestion(
        UUID questionId,
        UUID interviewId,
        UUID roundId,
        ActorId ownerId,
        UUID parentQuestionId,
        InterviewQuestionType questionType,
        String questionText,
        int difficulty,
        List<String> targetSkills,
        List<String> evaluationPoints,
        boolean followUpAllowed,
        boolean followUp,
        List<String> evidenceReferenceIds,
        String modelRequestId,
        String promptVersion,
        String contentHash,
        Instant createdAt
) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public InterviewQuestion {
        Objects.requireNonNull(questionId, "questionId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(roundId, "roundId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(questionType, "questionType不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");

        requireText(questionText, "questionText", 2000);
        requireText(modelRequestId, "modelRequestId", 128);
        requireText(promptVersion, "promptVersion", 64);
        if (difficulty < 1 || difficulty > 5) {
            throw new IllegalArgumentException("difficulty必须在1到5之间");
        }
        if (followUp != (parentQuestionId != null)) {
            throw new IllegalArgumentException("followUp与parentQuestionId不匹配");
        }

        targetSkills = requireList(targetSkills, "targetSkills", 1, 10, 500, false);
        evaluationPoints = requireList(evaluationPoints, "evaluationPoints", 1, 10, 500, false);
        evidenceReferenceIds = requireList(
                evidenceReferenceIds, "evidenceReferenceIds", 0, 10, 64, true
        );
        contentHash = requireSha256(contentHash, "contentHash");
    }

    private static List<String> requireList(
            List<String> values,
            String fieldName,
            int minSize,
            int maxSize,
            int maxItemLength,
            boolean sha256Only
    ) {
        if (values == null || values.size() < minSize || values.size() > maxSize) {
            throw new IllegalArgumentException(fieldName + "数量必须在" + minSize + "到" + maxSize + "之间");
        }

        List<String> copy = List.copyOf(values);
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException(fieldName + "不能包含重复值");
        }

        for (String value : copy) {
            requireText(value, fieldName + "元素", maxItemLength);
            if (sha256Only) requireSha256(value, fieldName + "元素");
        }
        return copy;
    }

    private static void requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能为空且长度不能超过" + maxLength);
        }
    }

    private static String requireSha256(String value, String fieldName) {
        if (value == null || !SHA256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + "必须是64位小写SHA-256");
        }
        return value;
    }
}