package com.leo.careerforgeai.interviewflow.application.port;

import com.leo.careerforgeai.interviewflow.domain.turn.InterviewAnswer;
import com.leo.careerforgeai.interviewflow.domain.turn.AnswerStatus;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义一题一答、owner隔离和草稿冻结CAS持久化端口
 * @author: Miao Zheng
 * @date: 2026-10-02
 */
public interface InterviewAnswerRepository {

    void insert(
            ActorId ownerId,
            UUID interviewId,
            AnswerRecord answer
    );

    Optional<AnswerRecord> findByQuestion(
            ActorId ownerId,
            UUID interviewId,
            UUID questionId
    );

    Optional<AnswerRecord> findAnswerByRequestId(
            ActorId ownerId,
            UUID requestId
    );

    default Optional<AnswerRecord> findById(
            ActorId ownerId,
            UUID interviewId,
            UUID answerId
    ) {
        return Optional.empty();
    }

    default List<AnswerRecord> findAnswersByInterview(
            ActorId ownerId,
            UUID interviewId
    ) {
        return List.of();
    }

    boolean updateIfVersionMatches(
            ActorId ownerId,
            UUID interviewId,
            AnswerRecord updated,
            long expectedVersion
    );

    /**
     * @param answer 草稿或已冻结答案领域对象
     * @param submitRequestId 提交或跳过幂等ID
     * @param submitRequestFingerprint 提交或跳过请求指纹
     * @param answerContentHash 冻结答案内容哈希
     * @param turnAnalysisJson 已校验的回合分析JSON
     * @param modelDecisionJson 已校验的模型建议JSON
     * @param finalDecision Java策略门最终决策
     * @param submittedAt 正式提交或跳过时间
     * @param version 持久化乐观锁版本
     */
    record AnswerRecord(
            InterviewAnswer answer,
            UUID submitRequestId,
            String submitRequestFingerprint,
            String answerContentHash,
            String turnAnalysisJson,
            String modelDecisionJson,
            FinalDecision finalDecision,
            Instant submittedAt,
            long version
    ) {

        public AnswerRecord {
            Objects.requireNonNull(answer, "answer不能为空");
            if (version < 0) {
                throw new IllegalArgumentException("version不能小于0");
            }
            boolean hasRequest = submitRequestId != null
                    && submitRequestFingerprint != null;
            if ((submitRequestId == null)
                    != (submitRequestFingerprint == null)) {
                throw new IllegalArgumentException("提交ID和指纹必须同时存在");
            }
            if (submitRequestFingerprint != null
                    && submitRequestFingerprint.length() != 64) {
                throw new IllegalArgumentException(
                        "submitRequestFingerprint必须为64位"
                );
            }
            if (answerContentHash != null
                    && answerContentHash.length() != 64) {
                throw new IllegalArgumentException(
                        "answerContentHash必须为64位"
                );
            }
            validateLifecycle(
                    answer.status(),
                    hasRequest,
                    answerContentHash,
                    submittedAt
            );
        }

        private static void validateLifecycle(
                AnswerStatus status,
                boolean hasRequest,
                String answerContentHash,
                Instant submittedAt
        ) {
            if (status == AnswerStatus.DRAFT) {
                if (hasRequest || answerContentHash != null
                        || submittedAt != null) {
                    throw new IllegalArgumentException("草稿不能包含提交事实");
                }
                return;
            }
            if (status == AnswerStatus.DISCARDED) {
                if (!hasRequest || answerContentHash != null
                        || submittedAt == null) {
                    throw new IllegalArgumentException("跳过答案事实不完整");
                }
                return;
            }
            if (!hasRequest || answerContentHash == null
                    || submittedAt == null) {
                throw new IllegalArgumentException("已提交答案事实不完整");
            }
        }
    }

    enum FinalDecision {
        FOLLOW_UP,
        NEXT_TOPIC,
        FINISH
    }
}
