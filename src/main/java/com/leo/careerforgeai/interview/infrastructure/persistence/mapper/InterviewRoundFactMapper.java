package com.leo.careerforgeai.interview.infrastructure.persistence.mapper;

import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewAnswerEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewQuestionEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewRoundEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 原子保存和查询面试回合、问题、答案并执行回合CAS更新
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Mapper
public interface InterviewRoundFactMapper {

    @Insert("""
            INSERT INTO interview_round (
                round_id, interview_id, owner_id, round_no, round_status,
                version, created_at, updated_at, answered_at, reviewed_at
            )
            VALUES (
                #{round.roundId}, #{round.interviewId}, #{round.ownerId},
                #{round.roundNo}, #{round.roundStatus}, #{round.version},
                #{round.createdAt}, #{round.updatedAt},
                #{round.answeredAt}, #{round.reviewedAt}
            )
            ON DUPLICATE KEY UPDATE round_id = round_id
            """)
    int claimRound(@Param("round") InterviewRoundEntity round);

    @Insert("""
            INSERT INTO interview_question (
                question_id, interview_id, round_id, owner_id,
                parent_question_id, question_type, question_text, difficulty,
                target_skills_json, evaluation_points_json,
                follow_up_allowed, is_follow_up, evidence_refs_json,
                model_request_id, prompt_version, content_hash, created_at
            )
            VALUES (
                #{question.questionId}, #{question.interviewId},
                #{question.roundId}, #{question.ownerId},
                #{question.parentQuestionId}, #{question.questionType},
                #{question.questionText}, #{question.difficulty},
                #{question.targetSkillsJson}, #{question.evaluationPointsJson},
                #{question.followUpAllowed}, #{question.followUp},
                #{question.evidenceRefsJson}, #{question.modelRequestId},
                #{question.promptVersion}, #{question.contentHash},
                #{question.createdAt}
            )
            ON DUPLICATE KEY UPDATE question_id = question_id
            """)
    int claimQuestion(@Param("question") InterviewQuestionEntity question);

    @Insert("""
            INSERT INTO interview_answer (
                answer_id, interview_id, round_id, question_id, owner_id,
                request_id, request_fingerprint, expected_interview_version,
                answer_text, content_hash, submitted_at
            )
            VALUES (
                #{answer.answerId}, #{answer.interviewId},
                #{answer.roundId}, #{answer.questionId}, #{answer.ownerId},
                #{answer.requestId}, #{answer.requestFingerprint},
                #{answer.expectedInterviewVersion}, #{answer.answerText},
                #{answer.contentHash}, #{answer.submittedAt}
            )
            ON DUPLICATE KEY UPDATE answer_id = answer_id
            """)
    int claimAnswer(@Param("answer") InterviewAnswerEntity answer);

    @Select("""
            SELECT round_id AS roundId, interview_id AS interviewId,
                   owner_id AS ownerId, round_no AS roundNo,
                   round_status AS roundStatus, version,
                   created_at AS createdAt, updated_at AS updatedAt,
                   answered_at AS answeredAt, reviewed_at AS reviewedAt
            FROM interview_round
            WHERE owner_id = #{ownerId}
              AND interview_id = #{interviewId}
              AND round_id = #{roundId}
            """)
    InterviewRoundEntity findRound(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("roundId") String roundId
    );

    @Select("""
            SELECT round_id AS roundId, interview_id AS interviewId,
                   owner_id AS ownerId, round_no AS roundNo,
                   round_status AS roundStatus, version,
                   created_at AS createdAt, updated_at AS updatedAt,
                   answered_at AS answeredAt, reviewed_at AS reviewedAt
            FROM interview_round
            WHERE owner_id = #{ownerId}
              AND interview_id = #{interviewId}
              AND round_no = #{roundNo}
            """)
    InterviewRoundEntity findRoundByNumber(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("roundNo") int roundNo
    );

    @Select("""
            SELECT question_id AS questionId, interview_id AS interviewId,
                   round_id AS roundId, owner_id AS ownerId,
                   parent_question_id AS parentQuestionId,
                   question_type AS questionType, question_text AS questionText,
                   difficulty, target_skills_json AS targetSkillsJson,
                   evaluation_points_json AS evaluationPointsJson,
                   follow_up_allowed AS followUpAllowed,
                   is_follow_up AS followUp,
                   evidence_refs_json AS evidenceRefsJson,
                   model_request_id AS modelRequestId,
                   prompt_version AS promptVersion,
                   content_hash AS contentHash, created_at AS createdAt
            FROM interview_question
            WHERE owner_id = #{ownerId}
              AND interview_id = #{interviewId}
              AND round_id = #{roundId}
            """)
    InterviewQuestionEntity findQuestionByRound(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("roundId") String roundId
    );

    @Select("""
            SELECT answer_id AS answerId, interview_id AS interviewId,
                   round_id AS roundId, question_id AS questionId,
                   owner_id AS ownerId, request_id AS requestId,
                   request_fingerprint AS requestFingerprint,
                   expected_interview_version AS expectedInterviewVersion,
                   answer_text AS answerText, content_hash AS contentHash,
                   submitted_at AS submittedAt
            FROM interview_answer
            WHERE owner_id = #{ownerId}
              AND interview_id = #{interviewId}
              AND question_id = #{questionId}
            """)
    InterviewAnswerEntity findAnswerByQuestion(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("questionId") String questionId
    );

    @Select("""
            SELECT answer_id AS answerId, interview_id AS interviewId,
                   round_id AS roundId, question_id AS questionId,
                   owner_id AS ownerId, request_id AS requestId,
                   request_fingerprint AS requestFingerprint,
                   expected_interview_version AS expectedInterviewVersion,
                   answer_text AS answerText, content_hash AS contentHash,
                   submitted_at AS submittedAt
            FROM interview_answer
            WHERE owner_id = #{ownerId}
              AND request_id = #{requestId}
            """)
    InterviewAnswerEntity findAnswerByRequest(
            @Param("ownerId") String ownerId,
            @Param("requestId") String requestId
    );

    @Update("""
            UPDATE interview_round
            SET round_status = #{roundStatus},
                version = #{newVersion},
                updated_at = #{updatedAt},
                answered_at = #{answeredAt},
                reviewed_at = #{reviewedAt}
            WHERE round_id = #{roundId}
              AND interview_id = #{interviewId}
              AND owner_id = #{ownerId}
              AND version = #{expectedVersion}
            """)
    int updateRoundIfVersionMatches(
            @Param("roundId") String roundId,
            @Param("interviewId") String interviewId,
            @Param("ownerId") String ownerId,
            @Param("roundStatus") String roundStatus,
            @Param("newVersion") long newVersion,
            @Param("updatedAt") Instant updatedAt,
            @Param("answeredAt") Instant answeredAt,
            @Param("reviewedAt") Instant reviewedAt,
            @Param("expectedVersion") long expectedVersion
    );

    @Select("""
        SELECT COUNT(*)
        FROM interview_question
        WHERE owner_id = #{ownerId}
          AND interview_id = #{interviewId}
        """)
    long countQuestions(@Param("ownerId") String ownerId, @Param("interviewId") String interviewId);

    @Select("""
        SELECT COUNT(*)
        FROM interview_question
        WHERE owner_id = #{ownerId}
          AND interview_id = #{interviewId}
          AND is_follow_up = TRUE
        """)
    long countFollowUps(@Param("ownerId") String ownerId, @Param("interviewId") String interviewId);

    @Select("""
        SELECT q.question_id AS questionId,
               q.interview_id AS interviewId,
               q.round_id AS roundId,
               q.owner_id AS ownerId,
               q.parent_question_id AS parentQuestionId,
               q.question_type AS questionType,
               q.question_text AS questionText,
               q.difficulty,
               q.target_skills_json AS targetSkillsJson,
               q.evaluation_points_json AS evaluationPointsJson,
               q.follow_up_allowed AS followUpAllowed,
               q.is_follow_up AS followUp,
               q.evidence_refs_json AS evidenceRefsJson,
               q.model_request_id AS modelRequestId,
               q.prompt_version AS promptVersion,
               q.content_hash AS contentHash,
               q.created_at AS createdAt
        FROM interview_question q
        INNER JOIN interview_round r
            ON r.round_id = q.round_id
           AND r.interview_id = q.interview_id
           AND r.owner_id = q.owner_id
        WHERE q.owner_id = #{ownerId}
          AND q.interview_id = #{interviewId}
        ORDER BY r.round_no ASC
        """)
    List<InterviewQuestionEntity> findQuestions(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId
    );
}