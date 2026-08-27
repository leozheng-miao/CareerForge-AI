package com.leo.careerforgeai.interview.application.port;

import com.leo.careerforgeai.interview.domain.EvidenceReview;
import com.leo.careerforgeai.interview.domain.TechnicalReview;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义技术评审和证据评审的幂等保存及owner隔离查询边界
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public interface InterviewReviewRepository {

    TechnicalReview claimTechnicalReview(TechnicalReview candidate);

    EvidenceReview claimEvidenceReview(EvidenceReview candidate);

    Optional<TechnicalReview> findTechnicalReviewByAnswer(
            ActorId ownerId,
            UUID interviewId,
            UUID answerId
    );

    Optional<EvidenceReview> findEvidenceReviewByAnswer(
            ActorId ownerId,
            UUID interviewId,
            UUID answerId
    );

    Optional<TechnicalReview> findTechnicalReviewById(
            ActorId ownerId,
            UUID interviewId,
            UUID technicalReviewId
    );

    Optional<EvidenceReview> findEvidenceReviewById(
            ActorId ownerId,
            UUID interviewId,
            UUID evidenceReviewId
    );
}