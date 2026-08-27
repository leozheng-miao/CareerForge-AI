package com.leo.careerforgeai.interview.domain;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 保存用户针对单个面试问题幂等提交的不可变原始答案
 * @author: Miao Zheng
 * @date: 2026-08-27
 * @param answerId 答案UUID
 * @param interviewId 所属面试UUID
 * @param roundId 所属回合UUID
 * @param questionId 所回答的问题UUID
 * @param ownerId 所属用户
 * @param requestId 客户端答案提交幂等UUID
 * @param requestFingerprint 答案请求的小写SHA-256
 * @param expectedInterviewVersion 客户端提交时看到的面试聚合版本
 * @param answerText 用户原始答案
 * @param contentHash 答案正文的小写SHA-256
 * @param submittedAt 提交时间
 **/
public record InterviewAnswer(
        UUID answerId,
        UUID interviewId,
        UUID roundId,
        UUID questionId,
        ActorId ownerId,
        UUID requestId,
        String requestFingerprint,
        long expectedInterviewVersion,
        String answerText,
        String contentHash,
        Instant submittedAt
) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public InterviewAnswer {
        Objects.requireNonNull(answerId, "answerId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(roundId, "roundId不能为空");
        Objects.requireNonNull(questionId, "questionId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(requestId, "requestId不能为空");
        Objects.requireNonNull(submittedAt, "submittedAt不能为空");

        if (expectedInterviewVersion < 0) {
            throw new IllegalArgumentException("expectedInterviewVersion不能小于0");
        }
        if (answerText == null || answerText.isBlank() || answerText.length() > 12000) {
            throw new IllegalArgumentException("answerText不能为空且长度不能超过12000");
        }

        requestFingerprint = requireSha256(requestFingerprint, "requestFingerprint");
        contentHash = requireSha256(contentHash, "contentHash");
    }

    private static String requireSha256(String value, String fieldName) {
        if (value == null || !SHA256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + "必须是64位小写SHA-256");
        }
        return value;
    }
}