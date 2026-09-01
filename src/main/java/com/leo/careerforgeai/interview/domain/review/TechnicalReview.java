package com.leo.careerforgeai.interview.domain.review;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存技术评审角色生成并经Java契约校验后的不可变评审事实
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param technicalReviewId 技术评审UUID
 * @param interviewId 所属面试UUID
 * @param roundId 所属回合UUID
 * @param questionId 所评问题UUID
 * @param answerId 所评答案UUID
 * @param ownerId 所属用户
 * @param dimensionScores 技术维度0至5分
 * @param coveredPoints 已覆盖要点
 * @param errorsOrOmissions 错误或遗漏
 * @param verificationBasis 可验证评价依据
 * @param suggestedFollowUp 建议追问，空字符串表示无需追问
 * @param modelRequestId 模型请求ID
 * @param promptVersion Prompt版本
 * @param inputHash 模型输入的小写SHA-256
 * @param outputHash 结构化输出的小写SHA-256
 * @param createdAt 创建时间
 **/
public record TechnicalReview(
        UUID technicalReviewId,
        UUID interviewId,
        UUID roundId,
        UUID questionId,
        UUID answerId,
        ActorId ownerId,
        Map<String, Integer> dimensionScores,
        List<String> coveredPoints,
        List<String> errorsOrOmissions,
        List<String> verificationBasis,
        String suggestedFollowUp,
        String modelRequestId,
        String promptVersion,
        String inputHash,
        String outputHash,
        Instant createdAt
) {

    private static final Pattern DIMENSION_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public TechnicalReview {
        Objects.requireNonNull(technicalReviewId, "technicalReviewId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(roundId, "roundId不能为空");
        Objects.requireNonNull(questionId, "questionId不能为空");
        Objects.requireNonNull(answerId, "answerId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");

        dimensionScores = validateScores(dimensionScores);
        coveredPoints = validateList(coveredPoints, "coveredPoints", 20);
        errorsOrOmissions = validateList(errorsOrOmissions, "errorsOrOmissions", 20);
        verificationBasis = validateList(verificationBasis, "verificationBasis", 20);

        if (suggestedFollowUp == null || suggestedFollowUp.length() > 2000) {
            throw new IllegalArgumentException("suggestedFollowUp不能为null且长度不能超过2000");
        }
        requireText(modelRequestId, "modelRequestId", 128);
        requireText(promptVersion, "promptVersion", 64);
        inputHash = requireSha256(inputHash, "inputHash");
        outputHash = requireSha256(outputHash, "outputHash");
    }

    private static Map<String, Integer> validateScores(Map<String, Integer> scores) {
        if (scores == null || scores.isEmpty() || scores.size() > 10) {
            throw new IllegalArgumentException("dimensionScores数量必须在1到10之间");
        }

        TreeMap<String, Integer> copy = new TreeMap<>();
        scores.forEach((dimension, score) -> {
            if (dimension == null || !DIMENSION_PATTERN.matcher(dimension).matches()) {
                throw new IllegalArgumentException("dimensionScores包含非法维度");
            }
            if (score == null || score < 0 || score > 5) {
                throw new IllegalArgumentException("dimensionScores分数必须在0到5之间");
            }
            copy.put(dimension, score);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static List<String> validateList(List<String> values, String fieldName, int maxSize) {
        if (values == null || values.size() > maxSize) {
            throw new IllegalArgumentException(fieldName + "数量不能超过" + maxSize);
        }
        List<String> copy = List.copyOf(values);
        for (String value : copy) requireText(value, fieldName + "元素", 500);
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