package com.leo.careerforgeai.interview.infrastructure.persistence.mapper;

import com.leo.careerforgeai.interview.infrastructure.persistence.entity.EvidenceReviewEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.TechnicalReviewEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @program: CareerForge-AI
 * @description: 幂等保存和owner隔离查询技术评审及证据评审事实
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Mapper
public interface InterviewReviewFactMapper {

    @Insert("""
            INSERT INTO interview_technical_review (
                technical_review_id, interview_id, round_id, question_id,
                answer_id, owner_id, dimension_scores_json,
                covered_points_json, errors_or_omissions_json,
                verification_basis_json, suggested_follow_up,
                model_request_id, prompt_version, input_hash, output_hash, created_at
            )
            VALUES (
                #{review.technicalReviewId}, #{review.interviewId},
                #{review.roundId}, #{review.questionId},
                #{review.answerId}, #{review.ownerId},
                #{review.dimensionScoresJson}, #{review.coveredPointsJson},
                #{review.errorsOrOmissionsJson}, #{review.verificationBasisJson},
                #{review.suggestedFollowUp}, #{review.modelRequestId},
                #{review.promptVersion}, #{review.inputHash},
                #{review.outputHash}, #{review.createdAt}
            )
            ON DUPLICATE KEY UPDATE technical_review_id = technical_review_id
            """)
    int claimTechnicalReview(@Param("review") TechnicalReviewEntity review);

    @Insert("""
            INSERT INTO interview_evidence_review (
                evidence_review_id, interview_id, round_id, question_id,
                answer_id, owner_id, review_source, verdict,
                evidence_reference_ids_json, reason,
                model_request_id, prompt_version,
                input_hash, output_hash, created_at
            )
            VALUES (
                #{review.evidenceReviewId}, #{review.interviewId},
                #{review.roundId}, #{review.questionId},
                #{review.answerId}, #{review.ownerId},
                #{review.reviewSource}, #{review.verdict},
                #{review.evidenceReferenceIdsJson}, #{review.reason},
                #{review.modelRequestId}, #{review.promptVersion},
                #{review.inputHash}, #{review.outputHash}, #{review.createdAt}
            )
            ON DUPLICATE KEY UPDATE evidence_review_id = evidence_review_id
            """)
    int claimEvidenceReview(@Param("review") EvidenceReviewEntity review);

    @Select("""
            SELECT technical_review_id AS technicalReviewId,
                   interview_id AS interviewId, round_id AS roundId,
                   question_id AS questionId, answer_id AS answerId,
                   owner_id AS ownerId,
                   dimension_scores_json AS dimensionScoresJson,
                   covered_points_json AS coveredPointsJson,
                   errors_or_omissions_json AS errorsOrOmissionsJson,
                   verification_basis_json AS verificationBasisJson,
                   suggested_follow_up AS suggestedFollowUp,
                   model_request_id AS modelRequestId,
                   prompt_version AS promptVersion,
                   input_hash AS inputHash, output_hash AS outputHash,
                   created_at AS createdAt
            FROM interview_technical_review
            WHERE owner_id = #{ownerId}
              AND interview_id = #{interviewId}
              AND technical_review_id = #{technicalReviewId}
            """)
    TechnicalReviewEntity findTechnicalReviewById(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("technicalReviewId") String technicalReviewId
    );

    @Select("""
            SELECT technical_review_id AS technicalReviewId,
                   interview_id AS interviewId, round_id AS roundId,
                   question_id AS questionId, answer_id AS answerId,
                   owner_id AS ownerId,
                   dimension_scores_json AS dimensionScoresJson,
                   covered_points_json AS coveredPointsJson,
                   errors_or_omissions_json AS errorsOrOmissionsJson,
                   verification_basis_json AS verificationBasisJson,
                   suggested_follow_up AS suggestedFollowUp,
                   model_request_id AS modelRequestId,
                   prompt_version AS promptVersion,
                   input_hash AS inputHash, output_hash AS outputHash,
                   created_at AS createdAt
            FROM interview_technical_review
            WHERE owner_id = #{ownerId}
              AND interview_id = #{interviewId}
              AND answer_id = #{answerId}
            """)
    TechnicalReviewEntity findTechnicalReviewByAnswer(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("answerId") String answerId
    );

    @Select("""
            SELECT evidence_review_id AS evidenceReviewId,
                   interview_id AS interviewId, round_id AS roundId,
                   question_id AS questionId, answer_id AS answerId,
                   owner_id AS ownerId, review_source AS reviewSource,
                   verdict, evidence_reference_ids_json AS evidenceReferenceIdsJson,
                   reason, model_request_id AS modelRequestId,
                   prompt_version AS promptVersion,
                   input_hash AS inputHash, output_hash AS outputHash,
                   created_at AS createdAt
            FROM interview_evidence_review
            WHERE owner_id = #{ownerId}
              AND interview_id = #{interviewId}
              AND evidence_review_id = #{evidenceReviewId}
            """)
    EvidenceReviewEntity findEvidenceReviewById(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("evidenceReviewId") String evidenceReviewId
    );

    @Select("""
            SELECT evidence_review_id AS evidenceReviewId,
                   interview_id AS interviewId, round_id AS roundId,
                   question_id AS questionId, answer_id AS answerId,
                   owner_id AS ownerId, review_source AS reviewSource,
                   verdict, evidence_reference_ids_json AS evidenceReferenceIdsJson,
                   reason, model_request_id AS modelRequestId,
                   prompt_version AS promptVersion,
                   input_hash AS inputHash, output_hash AS outputHash,
                   created_at AS createdAt
            FROM interview_evidence_review
            WHERE owner_id = #{ownerId}
              AND interview_id = #{interviewId}
              AND answer_id = #{answerId}
            """)
    EvidenceReviewEntity findEvidenceReviewByAnswer(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("answerId") String answerId
    );
}