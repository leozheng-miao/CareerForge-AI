package com.leo.careerforgeai.interview.infrastructure.persistence.converter;

import com.leo.careerforgeai.interview.domain.review.EvidenceConsistencyVerdict;
import com.leo.careerforgeai.interview.domain.review.EvidenceReview;
import com.leo.careerforgeai.interview.domain.review.EvidenceReviewSource;
import com.leo.careerforgeai.interview.domain.review.TechnicalReview;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.EvidenceReviewEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.TechnicalReviewEntity;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 转换技术评审、证据评审领域对象和数据库Entity
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Component
public class InterviewReviewPersistenceConverter {

    private static final TypeReference<Map<String, Integer>> SCORE_MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private final JsonMapper jsonMapper;

    public InterviewReviewPersistenceConverter(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper不能为空");
    }

    public TechnicalReviewEntity toEntity(TechnicalReview review) {
        Objects.requireNonNull(review, "review不能为空");
        TechnicalReviewEntity entity = new TechnicalReviewEntity();
        entity.setTechnicalReviewId(review.technicalReviewId().toString());
        entity.setInterviewId(review.interviewId().toString());
        entity.setRoundId(review.roundId().toString());
        entity.setQuestionId(review.questionId().toString());
        entity.setAnswerId(review.answerId().toString());
        entity.setOwnerId(review.ownerId().value());
        entity.setDimensionScoresJson(serialize(review.dimensionScores(), "dimensionScores"));
        entity.setCoveredPointsJson(serialize(review.coveredPoints(), "coveredPoints"));
        entity.setErrorsOrOmissionsJson(serialize(review.errorsOrOmissions(), "errorsOrOmissions"));
        entity.setVerificationBasisJson(serialize(review.verificationBasis(), "verificationBasis"));
        entity.setSuggestedFollowUp(review.suggestedFollowUp());
        entity.setModelRequestId(review.modelRequestId());
        entity.setPromptVersion(review.promptVersion());
        entity.setInputHash(review.inputHash());
        entity.setOutputHash(review.outputHash());
        entity.setCreatedAt(review.createdAt());
        return entity;
    }

    public EvidenceReviewEntity toEntity(EvidenceReview review) {
        Objects.requireNonNull(review, "review不能为空");
        EvidenceReviewEntity entity = new EvidenceReviewEntity();
        entity.setEvidenceReviewId(review.evidenceReviewId().toString());
        entity.setInterviewId(review.interviewId().toString());
        entity.setRoundId(review.roundId().toString());
        entity.setQuestionId(review.questionId().toString());
        entity.setAnswerId(review.answerId().toString());
        entity.setOwnerId(review.ownerId().value());
        entity.setReviewSource(review.source().name());
        entity.setVerdict(review.verdict().name());
        entity.setEvidenceReferenceIdsJson(
                serialize(review.evidenceReferenceIds(), "evidenceReferenceIds")
        );
        entity.setReason(review.reason());
        entity.setModelRequestId(review.modelRequestId());
        entity.setPromptVersion(review.promptVersion());
        entity.setInputHash(review.inputHash());
        entity.setOutputHash(review.outputHash());
        entity.setCreatedAt(review.createdAt());
        return entity;
    }

    public TechnicalReview toDomain(TechnicalReviewEntity entity) {
        return new TechnicalReview(
                UUID.fromString(entity.getTechnicalReviewId()),
                UUID.fromString(entity.getInterviewId()),
                UUID.fromString(entity.getRoundId()),
                UUID.fromString(entity.getQuestionId()),
                UUID.fromString(entity.getAnswerId()),
                new ActorId(entity.getOwnerId()),
                deserialize(entity.getDimensionScoresJson(), SCORE_MAP_TYPE, "dimensionScoresJson"),
                deserialize(entity.getCoveredPointsJson(), STRING_LIST_TYPE, "coveredPointsJson"),
                deserialize(entity.getErrorsOrOmissionsJson(), STRING_LIST_TYPE, "errorsOrOmissionsJson"),
                deserialize(entity.getVerificationBasisJson(), STRING_LIST_TYPE, "verificationBasisJson"),
                entity.getSuggestedFollowUp(),
                entity.getModelRequestId(),
                entity.getPromptVersion(),
                entity.getInputHash(),
                entity.getOutputHash(),
                entity.getCreatedAt()
        );
    }

    public EvidenceReview toDomain(EvidenceReviewEntity entity) {
        return new EvidenceReview(
                UUID.fromString(entity.getEvidenceReviewId()),
                UUID.fromString(entity.getInterviewId()),
                UUID.fromString(entity.getRoundId()),
                UUID.fromString(entity.getQuestionId()),
                UUID.fromString(entity.getAnswerId()),
                new ActorId(entity.getOwnerId()),
                EvidenceReviewSource.valueOf(entity.getReviewSource()),
                EvidenceConsistencyVerdict.valueOf(entity.getVerdict()),
                deserialize(
                        entity.getEvidenceReferenceIdsJson(),
                        STRING_LIST_TYPE,
                        "evidenceReferenceIdsJson"
                ),
                entity.getReason(),
                entity.getModelRequestId(),
                entity.getPromptVersion(),
                entity.getInputHash(),
                entity.getOutputHash(),
                entity.getCreatedAt()
        );
    }

    private String serialize(Object value, String fieldName) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化" + fieldName + "失败", exception);
        }
    }

    private <T> T deserialize(String json, TypeReference<T> type, String fieldName) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("反序列化" + fieldName + "失败", exception);
        }
    }
}