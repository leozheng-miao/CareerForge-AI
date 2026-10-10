package com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowTopicEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 按面试稳定顺序持久化和查询考查目标
 * @author: Miao Zheng
 * @date: 2026-10-03
 **/
@Mapper
public interface InterviewFlowTopicMapper
        extends BaseMapper<InterviewFlowTopicEntity> {

    @Select("""
            SELECT topic_id AS topicId, session_id AS interviewId,
                   owner_id AS ownerId, sequence_no AS sequenceNo,
                   topic_type AS topicType, objective,
                   topic_status AS topicStatus,
                   coverage_result_json AS coverageResultJson,
                   summary_json AS summaryJson,
                   summary_version AS summaryVersion,
                   plan_version AS planVersion, version,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM interview_flow_topic
            WHERE owner_id = #{ownerId}
              AND session_id = #{interviewId}
              AND topic_id = #{topicId}
            """)
    InterviewFlowTopicEntity selectByOwnerAndId(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("topicId") String topicId
    );

    @Select("""
            SELECT topic_id AS topicId, session_id AS interviewId,
                   owner_id AS ownerId, sequence_no AS sequenceNo,
                   topic_type AS topicType, objective,
                   topic_status AS topicStatus,
                   coverage_result_json AS coverageResultJson,
                   summary_json AS summaryJson,
                   summary_version AS summaryVersion,
                   plan_version AS planVersion, version,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM interview_flow_topic
            WHERE owner_id = #{ownerId}
              AND session_id = #{interviewId}
            ORDER BY sequence_no ASC, topic_id ASC
            """)
    List<InterviewFlowTopicEntity> selectBySessionOrder(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId
    );

    @Update("""
            UPDATE interview_flow_topic
            SET topic_status = #{topic.topicStatus},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(6)
            WHERE topic_id = #{topic.topicId}
              AND session_id = #{interviewId}
              AND owner_id = #{ownerId}
              AND version = #{expectedVersion}
            """)
    int updateIfVersionMatches(
            @Param("topic") InterviewFlowTopicEntity topic,
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("expectedVersion") long expectedVersion
    );

    @Update("""
            UPDATE interview_flow_topic
            SET topic_status = 'INVALIDATED', version = version + 1,
                updated_at = CURRENT_TIMESTAMP(6)
            WHERE owner_id = #{ownerId} AND session_id = #{interviewId}
              AND plan_version = #{planVersion}
              AND topic_status IN ('PLANNED', 'ACTIVE')
            """)
    int invalidateForReplanning(@Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("planVersion") long planVersion);
}
