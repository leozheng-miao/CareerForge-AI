package com.leo.careerforgeai.interview.domain.review;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存回答与冻结个人证据之间的不可变一致性评审事实
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param evidenceReviewId 证据评审UUID
 * @param interviewId 所属面试UUID
 * @param roundId 所属回合UUID
 * @param questionId 所评问题UUID
 * @param answerId 所评答案UUID
 * @param ownerId 所属用户
 * @param source 评审来源
 * @param verdict 证据一致性结论
 * @param evidenceReferenceIds 支持结论的证据片段ID
 * @param reason 安全且可审计的结论理由
 * @param modelRequestId 模型请求ID，Java评审时为空
 * @param promptVersion Prompt版本，Java评审时为空
 * @param inputHash 评审输入的小写SHA-256
 * @param outputHash 评审输出的小写SHA-256
 * @param createdAt 创建时间
 **/
public record EvidenceReview(
        UUID evidenceReviewId,
        UUID interviewId,
        UUID roundId,
        UUID questionId,
        UUID answerId,
        ActorId ownerId,
        EvidenceReviewSource source,
        EvidenceConsistencyVerdict verdict,
        List<String> evidenceReferenceIds,
        String reason,
        String modelRequestId,
        String promptVersion,
        String inputHash,
        String outputHash,
        Instant createdAt
) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public EvidenceReview {
        Objects.requireNonNull(evidenceReviewId, "evidenceReviewId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(roundId, "roundId不能为空");
        Objects.requireNonNull(questionId, "questionId不能为空");
        Objects.requireNonNull(answerId, "answerId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(source, "source不能为空");
        Objects.requireNonNull(verdict, "verdict不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");

        evidenceReferenceIds = validateReferences(evidenceReferenceIds);
        requireText(reason, "reason", 2000);
        inputHash = requireSha256(inputHash, "inputHash");
        outputHash = requireSha256(outputHash, "outputHash");

        boolean referenceRequired = verdict == EvidenceConsistencyVerdict.SUPPORTED
                || verdict == EvidenceConsistencyVerdict.PARTIALLY_SUPPORTED
                || verdict == EvidenceConsistencyVerdict.CONTRADICTED;
        if (referenceRequired && evidenceReferenceIds.isEmpty()) {
            throw new IllegalArgumentException("当前verdict必须包含证据引用");
        }
        if (verdict == EvidenceConsistencyVerdict.NOT_APPLICABLE
                && !evidenceReferenceIds.isEmpty()) {
            throw new IllegalArgumentException("NOT_APPLICABLE不能包含证据引用");
        }

        if (source == EvidenceReviewSource.JAVA) {
            if (verdict != EvidenceConsistencyVerdict.NOT_APPLICABLE) {
                throw new IllegalArgumentException("Java证据评审只能返回NOT_APPLICABLE");
            }
            if (modelRequestId != null || promptVersion != null) {
                throw new IllegalArgumentException("Java证据评审不能包含模型来源");
            }
        } else {
            requireText(modelRequestId, "modelRequestId", 128);
            requireText(promptVersion, "promptVersion", 64);
        }
    }

    private static List<String> validateReferences(List<String> references) {
        if (references == null || references.size() > 10) {
            throw new IllegalArgumentException("evidenceReferenceIds数量不能超过10");
        }

        List<String> copy = List.copyOf(references);
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException("evidenceReferenceIds不能重复");
        }
        for (String reference : copy) requireSha256(reference, "evidenceReferenceId");
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