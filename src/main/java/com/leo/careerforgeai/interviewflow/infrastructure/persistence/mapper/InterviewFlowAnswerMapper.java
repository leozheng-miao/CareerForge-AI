package com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowAnswerEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 持久化一题一答事实并执行owner隔离的草稿冻结CAS
 * @author: Miao Zheng
 * @date: 2026-10-03
 **/
@Mapper
public interface InterviewFlowAnswerMapper
        extends BaseMapper<InterviewFlowAnswerEntity> {

    @Select("""
            SELECT answer_id AS answerId, session_id AS interviewId,
                   question_id AS questionId, owner_id AS ownerId,
                   draft_text AS draftText,
                   draft_version AS draftVersion,
                   frozen_answer AS frozenAnswer,
                   answer_content_hash AS answerContentHash,
                   submit_request_id AS submitRequestId,
                   submit_request_fingerprint AS submitRequestFingerprint,
                   answer_status AS answerStatus,
                   turn_analysis_json AS turnAnalysisJson,
                   model_decision_json AS modelDecisionJson,
                   final_decision AS finalDecision,
                   submitted_at AS submittedAt, version,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM interview_flow_answer
            WHERE owner_id = #{ownerId}
              AND session_id = #{interviewId}
              AND question_id = #{questionId}
            """)
    InterviewFlowAnswerEntity selectByQuestion(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("questionId") String questionId
    );

    @Select("""
            SELECT answer_id AS answerId, session_id AS interviewId,
                   question_id AS questionId, owner_id AS ownerId,
                   draft_text AS draftText,
                   draft_version AS draftVersion,
                   frozen_answer AS frozenAnswer,
                   answer_content_hash AS answerContentHash,
                   submit_request_id AS submitRequestId,
                   submit_request_fingerprint AS submitRequestFingerprint,
                   answer_status AS answerStatus,
                   turn_analysis_json AS turnAnalysisJson,
                   model_decision_json AS modelDecisionJson,
                   final_decision AS finalDecision,
                   submitted_at AS submittedAt, version,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM interview_flow_answer
            WHERE owner_id = #{ownerId}
              AND submit_request_id = #{requestId}
            """)
    InterviewFlowAnswerEntity selectByOwnerAndRequestId(
            @Param("ownerId") String ownerId,
            @Param("requestId") String requestId
    );

    @Select("""
            SELECT answer_id AS answerId, session_id AS interviewId,
                   question_id AS questionId, owner_id AS ownerId,
                   draft_text AS draftText,
                   draft_version AS draftVersion,
                   frozen_answer AS frozenAnswer,
                   answer_content_hash AS answerContentHash,
                   submit_request_id AS submitRequestId,
                   submit_request_fingerprint AS submitRequestFingerprint,
                   answer_status AS answerStatus,
                   turn_analysis_json AS turnAnalysisJson,
                   model_decision_json AS modelDecisionJson,
                   final_decision AS finalDecision,
                   submitted_at AS submittedAt, version,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM interview_flow_answer
            WHERE owner_id = #{ownerId}
              AND session_id = #{interviewId}
              AND answer_id = #{answerId}
            """)
    InterviewFlowAnswerEntity selectByOwnerAndId(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("answerId") String answerId
    );

    @Select("""
            SELECT a.answer_id AS answerId, a.session_id AS interviewId,
                   a.question_id AS questionId, a.owner_id AS ownerId,
                   a.draft_text AS draftText, a.draft_version AS draftVersion,
                   a.frozen_answer AS frozenAnswer,
                   a.answer_content_hash AS answerContentHash,
                   a.submit_request_id AS submitRequestId,
                   a.submit_request_fingerprint AS submitRequestFingerprint,
                   a.answer_status AS answerStatus,
                   a.turn_analysis_json AS turnAnalysisJson,
                   a.model_decision_json AS modelDecisionJson,
                   a.final_decision AS finalDecision,
                   a.submitted_at AS submittedAt, a.version,
                   a.created_at AS createdAt, a.updated_at AS updatedAt
            FROM interview_flow_answer a
            JOIN interview_flow_question q ON q.question_id = a.question_id
            WHERE a.owner_id = #{ownerId}
              AND a.session_id = #{interviewId}
            ORDER BY q.display_order ASC, a.answer_id ASC
            """)
    List<InterviewFlowAnswerEntity> selectByInterview(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId
    );

    @Update("""
            UPDATE interview_flow_answer
            SET draft_text = #{answer.draftText},
                draft_version = CASE
                    WHEN NOT (draft_text <=> #{answer.draftText})
                        THEN draft_version + 1
                    ELSE draft_version
                END,
                frozen_answer = #{answer.frozenAnswer},
                answer_content_hash = #{answer.answerContentHash},
                submit_request_id = #{answer.submitRequestId},
                submit_request_fingerprint =
                    #{answer.submitRequestFingerprint},
                answer_status = #{answer.answerStatus},
                turn_analysis_json = #{answer.turnAnalysisJson},
                model_decision_json = #{answer.modelDecisionJson},
                final_decision = #{answer.finalDecision},
                submitted_at = #{answer.submittedAt},
                version = #{answer.version},
                updated_at = #{answer.updatedAt}
            WHERE answer_id = #{answer.answerId}
              AND session_id = #{interviewId}
              AND owner_id = #{ownerId}
              AND version = #{expectedVersion}
            """)
    int updateIfVersionMatches(
            @Param("answer") InterviewFlowAnswerEntity answer,
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("expectedVersion") long expectedVersion
    );
}
