package com.leo.careerforgeai.interview.infrastructure.persistence.adapter;

import com.leo.careerforgeai.interview.application.port.InterviewReviewRepository;
import com.leo.careerforgeai.interview.domain.EvidenceReview;
import com.leo.careerforgeai.interview.domain.TechnicalReview;
import com.leo.careerforgeai.interview.infrastructure.persistence.converter.InterviewReviewPersistenceConverter;
import com.leo.careerforgeai.interview.infrastructure.persistence.mapper.InterviewReviewFactMapper;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 幂等保存并按owner读取技术评审和证据评审事实
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Repository
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public final class MyBatisInterviewReviewAdapter implements InterviewReviewRepository {

    private final InterviewReviewFactMapper mapper;
    private final InterviewReviewPersistenceConverter converter;

    public MyBatisInterviewReviewAdapter(
            InterviewReviewFactMapper mapper,
            InterviewReviewPersistenceConverter converter
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper不能为空");
        this.converter = Objects.requireNonNull(converter, "converter不能为空");
    }

    @Override
    public TechnicalReview claimTechnicalReview(TechnicalReview candidate) {
        Objects.requireNonNull(candidate, "candidate不能为空");
        mapper.claimTechnicalReview(converter.toEntity(candidate));
        return findTechnicalReviewByAnswer(
                candidate.ownerId(),
                candidate.interviewId(),
                candidate.answerId()
        ).orElseThrow(() -> new IllegalStateException("技术评审认领后无法读取"));
    }

    @Override
    public EvidenceReview claimEvidenceReview(EvidenceReview candidate) {
        Objects.requireNonNull(candidate, "candidate不能为空");
        mapper.claimEvidenceReview(converter.toEntity(candidate));
        return findEvidenceReviewByAnswer(
                candidate.ownerId(),
                candidate.interviewId(),
                candidate.answerId()
        ).orElseThrow(() -> new IllegalStateException("证据评审认领后无法读取"));
    }

    @Override
    public Optional<TechnicalReview> findTechnicalReviewById(
            ActorId ownerId,
            UUID interviewId,
            UUID technicalReviewId
    ) {
        requireScope(ownerId, interviewId, technicalReviewId, "technicalReviewId");
        return Optional.ofNullable(mapper.findTechnicalReviewById(
                ownerId.value(),
                interviewId.toString(),
                technicalReviewId.toString()
        )).map(converter::toDomain);
    }

    @Override
    public Optional<EvidenceReview> findEvidenceReviewById(
            ActorId ownerId,
            UUID interviewId,
            UUID evidenceReviewId
    ) {
        requireScope(ownerId, interviewId, evidenceReviewId, "evidenceReviewId");
        return Optional.ofNullable(mapper.findEvidenceReviewById(
                ownerId.value(),
                interviewId.toString(),
                evidenceReviewId.toString()
        )).map(converter::toDomain);
    }

    @Override
    public Optional<TechnicalReview> findTechnicalReviewByAnswer(
            ActorId ownerId,
            UUID interviewId,
            UUID answerId
    ) {
        requireScope(ownerId, interviewId, answerId, "answerId");
        return Optional.ofNullable(mapper.findTechnicalReviewByAnswer(
                ownerId.value(),
                interviewId.toString(),
                answerId.toString()
        )).map(converter::toDomain);
    }

    @Override
    public Optional<EvidenceReview> findEvidenceReviewByAnswer(
            ActorId ownerId,
            UUID interviewId,
            UUID answerId
    ) {
        requireScope(ownerId, interviewId, answerId, "answerId");
        return Optional.ofNullable(mapper.findEvidenceReviewByAnswer(
                ownerId.value(),
                interviewId.toString(),
                answerId.toString()
        )).map(converter::toDomain);
    }

    private static void requireScope(ActorId ownerId, UUID interviewId, UUID id, String fieldName) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(id, fieldName + "不能为空");
    }
}