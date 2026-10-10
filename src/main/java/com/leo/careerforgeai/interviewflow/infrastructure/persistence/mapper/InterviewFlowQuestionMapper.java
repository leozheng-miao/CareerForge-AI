package com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowQuestionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 持久化带题源的问题并按展示序稳定查询
 * @author: Miao Zheng
 * @date: 2026-10-03
 **/
@Mapper
public interface InterviewFlowQuestionMapper
        extends BaseMapper<InterviewFlowQuestionEntity> {

    @Select("""
            SELECT question_id AS questionId,
                   session_id AS interviewId, topic_id AS topicId,
                   owner_id AS ownerId,
                   parent_question_id AS parentQuestionId,
                   question_type AS questionType,
                   display_order AS displayOrder,
                   question_text AS questionText,
                   source_refs_json AS sourceRefsJson,
                   content_fingerprint AS contentFingerprint,
                   question_status AS questionStatus,
                   plan_version AS planVersion, version,
                   presented_at AS presentedAt, closed_at AS closedAt,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM interview_flow_question
            WHERE owner_id = #{ownerId}
              AND session_id = #{interviewId}
              AND question_id = #{questionId}
            """)
    InterviewFlowQuestionEntity selectByOwnerAndId(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("questionId") String questionId
    );

    @Select("""
            SELECT question_id AS questionId,
                   session_id AS interviewId, topic_id AS topicId,
                   owner_id AS ownerId,
                   parent_question_id AS parentQuestionId,
                   question_type AS questionType,
                   display_order AS displayOrder,
                   question_text AS questionText,
                   source_refs_json AS sourceRefsJson,
                   content_fingerprint AS contentFingerprint,
                   question_status AS questionStatus,
                   plan_version AS planVersion, version,
                   presented_at AS presentedAt, closed_at AS closedAt,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM interview_flow_question
            WHERE owner_id = #{ownerId}
              AND session_id = #{interviewId}
            ORDER BY display_order ASC, question_id ASC
            """)
    List<InterviewFlowQuestionEntity> selectBySessionOrder(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId
    );

    @Update("""
            UPDATE interview_flow_question
            SET question_status = #{question.questionStatus},
                version = version + 1,
                presented_at = CASE
                    WHEN #{question.questionStatus} = 'PRESENTED'
                        THEN COALESCE(presented_at, CURRENT_TIMESTAMP(6))
                    ELSE presented_at
                END,
                closed_at = CASE
                    WHEN #{question.questionStatus} IN ('CLOSED', 'SKIPPED')
                        THEN COALESCE(closed_at, CURRENT_TIMESTAMP(6))
                    ELSE closed_at
                END,
                updated_at = CURRENT_TIMESTAMP(6)
            WHERE question_id = #{question.questionId}
              AND session_id = #{interviewId}
              AND owner_id = #{ownerId}
              AND version = #{expectedVersion}
            """)
    int updateIfVersionMatches(
            @Param("question") InterviewFlowQuestionEntity question,
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("expectedVersion") long expectedVersion
    );

    @Update("""
            UPDATE interview_flow_question
            SET question_status = 'INVALIDATED', version = version + 1,
                updated_at = CURRENT_TIMESTAMP(6)
            WHERE owner_id = #{ownerId} AND session_id = #{interviewId}
              AND plan_version = #{planVersion}
              AND question_status IN ('RESERVED', 'PRESENTED')
            """)
    int invalidateForReplanning(@Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("planVersion") long planVersion);
}
