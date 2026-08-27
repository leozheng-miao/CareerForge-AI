package com.leo.careerforgeai.interview.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.MockInterviewSessionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 操作mock_interview_session并执行幂等认领和owner受控CAS更新
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Mapper
public interface MockInterviewSessionMapper extends BaseMapper<MockInterviewSessionEntity> {

    @Insert("""
            INSERT INTO mock_interview_session (
                interview_id, owner_id, request_id, request_fingerprint,
                input_snapshot_id, input_snapshot_hash,
                interview_mode, interview_status,
                max_questions, max_follow_ups, max_model_calls, max_total_tokens,
                failure_code, version, created_at, updated_at, finished_at
            )
            VALUES (
                #{session.interviewId}, #{session.ownerId},
                #{session.requestId}, #{session.requestFingerprint},
                #{session.inputSnapshotId}, #{session.inputSnapshotHash},
                #{session.interviewMode}, #{session.interviewStatus},
                #{session.maxQuestions}, #{session.maxFollowUps},
                #{session.maxModelCalls}, #{session.maxTotalTokens},
                #{session.failureCode}, #{session.version},
                #{session.createdAt}, #{session.updatedAt}, #{session.finishedAt}
            )
            ON DUPLICATE KEY UPDATE interview_id = interview_id
            """)
    int claim(@Param("session") MockInterviewSessionEntity session);

    @Update("""
            UPDATE mock_interview_session
            SET interview_status = #{interviewStatus},
                failure_code = #{failureCode},
                version = #{newVersion},
                updated_at = #{updatedAt},
                finished_at = #{finishedAt}
            WHERE interview_id = #{interviewId}
              AND owner_id = #{ownerId}
              AND version = #{expectedVersion}
            """)
    int updateIfVersionMatches(
            @Param("interviewId") String interviewId,
            @Param("ownerId") String ownerId,
            @Param("interviewStatus") String interviewStatus,
            @Param("failureCode") String failureCode,
            @Param("newVersion") long newVersion,
            @Param("updatedAt") Instant updatedAt,
            @Param("finishedAt") Instant finishedAt,
            @Param("expectedVersion") long expectedVersion
    );
}