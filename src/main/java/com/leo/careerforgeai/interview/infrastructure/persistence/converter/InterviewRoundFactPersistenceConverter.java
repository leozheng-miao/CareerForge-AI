package com.leo.careerforgeai.interview.infrastructure.persistence.converter;

import com.leo.careerforgeai.interview.domain.InterviewAnswer;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewQuestionType;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewRoundStatus;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewAnswerEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewQuestionEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewRoundEntity;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 转换面试回合、问题、答案领域对象和数据库Entity
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Component
public final class InterviewRoundFactPersistenceConverter {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private final JsonMapper jsonMapper;

    public InterviewRoundFactPersistenceConverter(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper不能为空");
    }

    public InterviewRoundEntity toEntity(InterviewRound round) {
        Objects.requireNonNull(round, "round不能为空");
        InterviewRoundEntity entity = new InterviewRoundEntity();
        entity.setRoundId(round.roundId().toString());
        entity.setInterviewId(round.interviewId().toString());
        entity.setOwnerId(round.ownerId().value());
        entity.setRoundNo(round.roundNo());
        entity.setRoundStatus(round.status().name());
        entity.setVersion(round.version());
        entity.setCreatedAt(round.createdAt());
        entity.setUpdatedAt(round.updatedAt());
        entity.setAnsweredAt(round.answeredAt());
        entity.setReviewedAt(round.reviewedAt());
        return entity;
    }

    public InterviewQuestionEntity toEntity(InterviewQuestion question) {
        Objects.requireNonNull(question, "question不能为空");
        InterviewQuestionEntity entity = new InterviewQuestionEntity();
        entity.setQuestionId(question.questionId().toString());
        entity.setInterviewId(question.interviewId().toString());
        entity.setRoundId(question.roundId().toString());
        entity.setOwnerId(question.ownerId().value());
        entity.setParentQuestionId(toNullableString(question.parentQuestionId()));
        entity.setQuestionType(question.questionType().name());
        entity.setQuestionText(question.questionText());
        entity.setDifficulty(question.difficulty());
        entity.setTargetSkillsJson(serialize(question.targetSkills(), "targetSkills"));
        entity.setEvaluationPointsJson(serialize(question.evaluationPoints(), "evaluationPoints"));
        entity.setFollowUpAllowed(question.followUpAllowed());
        entity.setFollowUp(question.followUp());
        entity.setEvidenceRefsJson(serialize(question.evidenceReferenceIds(), "evidenceReferenceIds"));
        entity.setModelRequestId(question.modelRequestId());
        entity.setPromptVersion(question.promptVersion());
        entity.setContentHash(question.contentHash());
        entity.setCreatedAt(question.createdAt());
        return entity;
    }

    public InterviewAnswerEntity toEntity(InterviewAnswer answer) {
        Objects.requireNonNull(answer, "answer不能为空");
        InterviewAnswerEntity entity = new InterviewAnswerEntity();
        entity.setAnswerId(answer.answerId().toString());
        entity.setInterviewId(answer.interviewId().toString());
        entity.setRoundId(answer.roundId().toString());
        entity.setQuestionId(answer.questionId().toString());
        entity.setOwnerId(answer.ownerId().value());
        entity.setRequestId(answer.requestId().toString());
        entity.setRequestFingerprint(answer.requestFingerprint());
        entity.setExpectedInterviewVersion(answer.expectedInterviewVersion());
        entity.setAnswerText(answer.answerText());
        entity.setContentHash(answer.contentHash());
        entity.setSubmittedAt(answer.submittedAt());
        return entity;
    }

    public InterviewRound toDomain(InterviewRoundEntity entity) {
        return new InterviewRound(
                UUID.fromString(entity.getRoundId()),
                UUID.fromString(entity.getInterviewId()),
                new ActorId(entity.getOwnerId()),
                requireInteger(entity.getRoundNo(), "roundNo"),
                InterviewRoundStatus.valueOf(entity.getRoundStatus()),
                requireLong(entity.getVersion(), "version"),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getAnsweredAt(),
                entity.getReviewedAt()
        );
    }

    public InterviewQuestion toDomain(InterviewQuestionEntity entity) {
        return new InterviewQuestion(
                UUID.fromString(entity.getQuestionId()),
                UUID.fromString(entity.getInterviewId()),
                UUID.fromString(entity.getRoundId()),
                new ActorId(entity.getOwnerId()),
                toNullableUuid(entity.getParentQuestionId()),
                InterviewQuestionType.valueOf(entity.getQuestionType()),
                entity.getQuestionText(),
                requireInteger(entity.getDifficulty(), "difficulty"),
                deserialize(entity.getTargetSkillsJson(), "targetSkillsJson"),
                deserialize(entity.getEvaluationPointsJson(), "evaluationPointsJson"),
                requireBoolean(entity.getFollowUpAllowed(), "followUpAllowed"),
                requireBoolean(entity.getFollowUp(), "followUp"),
                deserialize(entity.getEvidenceRefsJson(), "evidenceRefsJson"),
                entity.getModelRequestId(),
                entity.getPromptVersion(),
                entity.getContentHash(),
                entity.getCreatedAt()
        );
    }

    public InterviewAnswer toDomain(InterviewAnswerEntity entity) {
        return new InterviewAnswer(
                UUID.fromString(entity.getAnswerId()),
                UUID.fromString(entity.getInterviewId()),
                UUID.fromString(entity.getRoundId()),
                UUID.fromString(entity.getQuestionId()),
                new ActorId(entity.getOwnerId()),
                UUID.fromString(entity.getRequestId()),
                entity.getRequestFingerprint(),
                requireLong(entity.getExpectedInterviewVersion(), "expectedInterviewVersion"),
                entity.getAnswerText(),
                entity.getContentHash(),
                entity.getSubmittedAt()
        );
    }

    private String serialize(List<String> values, String fieldName) {
        try {
            return jsonMapper.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化" + fieldName + "失败", exception);
        }
    }

    private List<String> deserialize(String json, String fieldName) {
        try {
            return jsonMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("反序列化" + fieldName + "失败", exception);
        }
    }

    private static String toNullableString(UUID value) {
        return value == null ? null : value.toString();
    }

    private static UUID toNullableUuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private static int requireInteger(Integer value, String fieldName) {
        if (value == null) throw new IllegalStateException("数据库" + fieldName + "不能为空");
        return value;
    }

    private static long requireLong(Long value, String fieldName) {
        if (value == null) throw new IllegalStateException("数据库" + fieldName + "不能为空");
        return value;
    }

    private static boolean requireBoolean(Boolean value, String fieldName) {
        if (value == null) throw new IllegalStateException("数据库" + fieldName + "不能为空");
        return value;
    }
}