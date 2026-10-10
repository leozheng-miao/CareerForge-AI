package com.leo.careerforgeai.interviewflow.application.port;

import com.leo.careerforgeai.interviewflow.domain.turn.InterviewQuestion;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义带来源的完整题目保存、owner查询和稳定顺序端口
 * @author: Miao Zheng
 * @date: 2026-10-02
 */
public interface InterviewQuestionRepository {

    void insertAll(
            ActorId ownerId,
            UUID interviewId,
            List<QuestionWrite> questions
    );

    Optional<InterviewQuestion> findQuestion(
            ActorId ownerId,
            UUID interviewId,
            UUID questionId
    );

    default Optional<QuestionRecord> findQuestionRecord(
            ActorId ownerId,
            UUID interviewId,
            UUID questionId
    ) {
        return findQuestion(ownerId, interviewId, questionId)
                .map(question -> new QuestionRecord(question, 0));
    }

    default Optional<QuestionDetails> findQuestionDetails(
            ActorId ownerId,
            UUID interviewId,
            UUID questionId
    ) {
        return findQuestionRecord(ownerId, interviewId, questionId)
                .map(record -> new QuestionDetails(
                        record.question(),
                        null,
                        "[]",
                        "",
                        1,
                        record.version()
                ));
    }

    /** 按displayOrder升序返回面试题目。 */
    List<InterviewQuestion> findQuestions(
            ActorId ownerId,
            UUID interviewId
    );

    boolean updateIfVersionMatches(
            ActorId ownerId,
            UUID interviewId,
            InterviewQuestion updated,
            long expectedVersion
    );

    default int invalidateQuestionsForReplanning(ActorId ownerId, UUID interviewId,
            long previousPlanVersion) {
        return 0;
    }

    /**
     * @param question 完整题目领域对象
     * @param parentQuestionId 追问的父问题ID，主问题为null
     * @param sourceRefsJson 题库来源和片段标识JSON数组
     * @param contentFingerprint 会话内去重指纹
     * @param planVersion 生成该题目时的面试计划版本
     */
    record QuestionWrite(
            InterviewQuestion question,
            UUID parentQuestionId,
            String sourceRefsJson,
            String contentFingerprint,
            long planVersion
    ) {

        public QuestionWrite {
            Objects.requireNonNull(question, "question不能为空");
            if (sourceRefsJson == null || sourceRefsJson.isBlank()) {
                throw new IllegalArgumentException("sourceRefsJson不能为空");
            }
            if (contentFingerprint == null
                    || contentFingerprint.length() != 64) {
                throw new IllegalArgumentException(
                        "contentFingerprint必须为64位"
                );
            }
            if (planVersion < 1) {
                throw new IllegalArgumentException("planVersion必须大于等于1");
            }
            boolean followUp = question.type()
                    == InterviewQuestion.QuestionType.FOLLOW_UP;
            if (followUp != (parentQuestionId != null)) {
                throw new IllegalArgumentException("追问必须关联父问题");
            }
        }
    }

    record QuestionRecord(
            InterviewQuestion question,
            long version
    ) {

        public QuestionRecord {
            Objects.requireNonNull(question, "question不能为null");
            if (version < 0) {
                throw new IllegalArgumentException("version不能小于0");
            }
        }
    }

    record QuestionDetails(
            InterviewQuestion question,
            UUID parentQuestionId,
            String sourceRefsJson,
            String contentFingerprint,
            long planVersion,
            long version
    ) {

        public QuestionDetails {
            Objects.requireNonNull(question, "question不能为空");
            if (sourceRefsJson == null || sourceRefsJson.isBlank()) {
                throw new IllegalArgumentException("sourceRefsJson不能为空");
            }
            contentFingerprint = contentFingerprint == null
                    ? ""
                    : contentFingerprint;
            if (!contentFingerprint.isEmpty()
                    && contentFingerprint.length() != 64) {
                throw new IllegalArgumentException(
                        "contentFingerprint必须为空或64位"
                );
            }
            if (planVersion < 1 || version < 0) {
                throw new IllegalArgumentException("题目版本不合法");
            }
        }
    }
}
