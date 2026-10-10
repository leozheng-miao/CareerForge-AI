package com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowSessionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 持久化面试会话并执行owner隔离、幂等认领和乐观锁更新
 * @author: Miao Zheng
 * @date: 2026-10-03
 **/
@Mapper
public interface InterviewFlowSessionMapper
        extends BaseMapper<InterviewFlowSessionEntity> {

    @Insert("""
            INSERT INTO interview_flow_session (
                interview_id, owner_id, request_id, request_fingerprint,
                interview_mode, session_stage, timer_state,
                duration_seconds, accumulated_answer_seconds,
                last_active_at, current_topic_id, current_question_id,
                submitted_answer_count, follow_up_count,
                consecutive_follow_up_count, input_version, plan_version,
                version, started_at, finished_at, failure_code,
                created_at, updated_at
            )
            VALUES (
                #{session.interviewId}, #{session.ownerId},
                #{session.requestId}, #{session.requestFingerprint},
                #{session.interviewMode}, #{session.sessionStage},
                #{session.timerState}, #{session.durationSeconds},
                #{session.accumulatedAnswerSeconds},
                #{session.lastActiveAt}, #{session.currentTopicId},
                #{session.currentQuestionId},
                #{session.submittedAnswerCount}, #{session.followUpCount},
                #{session.consecutiveFollowUpCount},
                #{session.inputVersion}, #{session.planVersion},
                #{session.version}, #{session.startedAt},
                #{session.finishedAt}, #{session.failureCode},
                #{session.createdAt}, #{session.updatedAt}
            )
            ON DUPLICATE KEY UPDATE interview_id = interview_id
            """)
    int claim(@Param("session") InterviewFlowSessionEntity session);

    @Select("""
            SELECT interview_id AS interviewId, owner_id AS ownerId,
                   request_id AS requestId,
                   request_fingerprint AS requestFingerprint,
                   interview_mode AS interviewMode,
                   session_stage AS sessionStage,
                   timer_state AS timerState,
                   duration_seconds AS durationSeconds,
                   accumulated_answer_seconds AS accumulatedAnswerSeconds,
                   last_active_at AS lastActiveAt,
                   current_topic_id AS currentTopicId,
                   current_question_id AS currentQuestionId,
                   submitted_answer_count AS submittedAnswerCount,
                   follow_up_count AS followUpCount,
                   consecutive_follow_up_count AS consecutiveFollowUpCount,
                   input_version AS inputVersion,
                   plan_version AS planVersion,
                   version, started_at AS startedAt,
                   finished_at AS finishedAt,
                   failure_code AS failureCode,
                   created_at AS createdAt, updated_at AS updatedAt,
                   active_owner_id AS activeOwnerId
            FROM interview_flow_session
            WHERE owner_id = #{ownerId}
              AND interview_id = #{interviewId}
            """)
    InterviewFlowSessionEntity selectByOwnerAndId(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId
    );

    @Select("""
            SELECT interview_id AS interviewId, owner_id AS ownerId,
                   request_id AS requestId,
                   request_fingerprint AS requestFingerprint,
                   interview_mode AS interviewMode,
                   session_stage AS sessionStage,
                   timer_state AS timerState,
                   duration_seconds AS durationSeconds,
                   accumulated_answer_seconds AS accumulatedAnswerSeconds,
                   last_active_at AS lastActiveAt,
                   current_topic_id AS currentTopicId,
                   current_question_id AS currentQuestionId,
                   submitted_answer_count AS submittedAnswerCount,
                   follow_up_count AS followUpCount,
                   consecutive_follow_up_count AS consecutiveFollowUpCount,
                   input_version AS inputVersion,
                   plan_version AS planVersion,
                   version, started_at AS startedAt,
                   finished_at AS finishedAt,
                   failure_code AS failureCode,
                   created_at AS createdAt, updated_at AS updatedAt,
                   active_owner_id AS activeOwnerId
            FROM interview_flow_session
            WHERE owner_id = #{ownerId}
              AND request_id = #{requestId}
            """)
    InterviewFlowSessionEntity selectByOwnerAndRequestId(
            @Param("ownerId") String ownerId,
            @Param("requestId") String requestId
    );

    @Select("""
            SELECT interview_id AS interviewId, owner_id AS ownerId,
                   request_id AS requestId,
                   request_fingerprint AS requestFingerprint,
                   interview_mode AS interviewMode,
                   session_stage AS sessionStage,
                   timer_state AS timerState,
                   duration_seconds AS durationSeconds,
                   accumulated_answer_seconds AS accumulatedAnswerSeconds,
                   last_active_at AS lastActiveAt,
                   current_topic_id AS currentTopicId,
                   current_question_id AS currentQuestionId,
                   submitted_answer_count AS submittedAnswerCount,
                   follow_up_count AS followUpCount,
                   consecutive_follow_up_count AS consecutiveFollowUpCount,
                   input_version AS inputVersion,
                   plan_version AS planVersion,
                   version, started_at AS startedAt,
                   finished_at AS finishedAt,
                   failure_code AS failureCode,
                   created_at AS createdAt, updated_at AS updatedAt,
                   active_owner_id AS activeOwnerId
            FROM interview_flow_session
            WHERE owner_id = #{ownerId}
              AND session_stage = 'INTERVIEWING'
            ORDER BY updated_at DESC, interview_id DESC
            LIMIT 1
            """)
    InterviewFlowSessionEntity selectInProgressByOwner(
            @Param("ownerId") String ownerId
    );

    @Select("""
            SELECT interview_id AS interviewId, owner_id AS ownerId,
                   request_id AS requestId,
                   request_fingerprint AS requestFingerprint,
                   interview_mode AS interviewMode,
                   session_stage AS sessionStage,
                   timer_state AS timerState,
                   duration_seconds AS durationSeconds,
                   accumulated_answer_seconds AS accumulatedAnswerSeconds,
                   last_active_at AS lastActiveAt,
                   current_topic_id AS currentTopicId,
                   current_question_id AS currentQuestionId,
                   submitted_answer_count AS submittedAnswerCount,
                   follow_up_count AS followUpCount,
                   consecutive_follow_up_count AS consecutiveFollowUpCount,
                   input_version AS inputVersion,
                   plan_version AS planVersion,
                   version, started_at AS startedAt,
                   finished_at AS finishedAt,
                   failure_code AS failureCode,
                   created_at AS createdAt, updated_at AS updatedAt,
                   active_owner_id AS activeOwnerId
            FROM interview_flow_session
            WHERE session_stage = 'INTERVIEWING'
              AND timer_state = 'RUNNING'
              AND last_active_at <= #{cutoff}
            ORDER BY last_active_at ASC, interview_id ASC
            LIMIT #{limit}
            """)
    List<InterviewFlowSessionEntity> selectRunningStartedBefore(
            @Param("cutoff") Instant cutoff,
            @Param("limit") int limit
    );

    @Select("""
            <script>
            SELECT interview_id AS interviewId, owner_id AS ownerId,
                   request_id AS requestId,
                   request_fingerprint AS requestFingerprint,
                   interview_mode AS interviewMode,
                   session_stage AS sessionStage, timer_state AS timerState,
                   duration_seconds AS durationSeconds,
                   accumulated_answer_seconds AS accumulatedAnswerSeconds,
                   last_active_at AS lastActiveAt,
                   current_topic_id AS currentTopicId,
                   current_question_id AS currentQuestionId,
                   submitted_answer_count AS submittedAnswerCount,
                   follow_up_count AS followUpCount,
                   consecutive_follow_up_count AS consecutiveFollowUpCount,
                   input_version AS inputVersion, plan_version AS planVersion,
                   version, started_at AS startedAt, finished_at AS finishedAt,
                   failure_code AS failureCode,
                   created_at AS createdAt, updated_at AS updatedAt,
                   active_owner_id AS activeOwnerId
            FROM interview_flow_session
            WHERE owner_id = #{ownerId}
            <if test="beforeCreatedAt != null and beforeInterviewId != null">
              AND (created_at &lt; #{beforeCreatedAt}
                   OR (created_at = #{beforeCreatedAt}
                       AND interview_id &lt; #{beforeInterviewId}))
            </if>
            ORDER BY created_at DESC, interview_id DESC
            LIMIT #{limit}
            </script>
            """)
    List<InterviewFlowSessionEntity> selectOwnerPage(
            @Param("ownerId") String ownerId,
            @Param("beforeCreatedAt") Instant beforeCreatedAt,
            @Param("beforeInterviewId") String beforeInterviewId,
            @Param("limit") int limit
    );

    @Update("""
            UPDATE interview_flow_session
            SET session_stage = #{session.sessionStage},
                timer_state = #{session.timerState},
                duration_seconds = #{session.durationSeconds},
                accumulated_answer_seconds =
                    #{session.accumulatedAnswerSeconds},
                last_active_at = #{session.lastActiveAt},
                submitted_answer_count = #{session.submittedAnswerCount},
                follow_up_count = #{session.followUpCount},
                consecutive_follow_up_count =
                    #{session.consecutiveFollowUpCount},
                version = #{session.version},
                started_at = CASE
                    WHEN #{session.sessionStage} = 'INTERVIEWING'
                        THEN COALESCE(started_at, CURRENT_TIMESTAMP(6))
                    ELSE started_at
                END,
                finished_at = CASE
                    WHEN #{session.sessionStage} IN ('COMPLETED', 'FAILED')
                        THEN COALESCE(finished_at, CURRENT_TIMESTAMP(6))
                    ELSE finished_at
                END,
                updated_at = CURRENT_TIMESTAMP(6)
            WHERE interview_id = #{session.interviewId}
              AND owner_id = #{ownerId}
              AND version = #{expectedVersion}
            """)
    int updateIfVersionMatches(
            @Param("session") InterviewFlowSessionEntity session,
            @Param("ownerId") String ownerId,
            @Param("expectedVersion") long expectedVersion
    );

    @Update("""
            UPDATE interview_flow_session
            SET session_stage = 'PREPARING', timer_state = 'NOT_STARTED',
                duration_seconds = #{durationSeconds},
                accumulated_answer_seconds = 0, last_active_at = NULL,
                current_topic_id = NULL, current_question_id = NULL,
                input_version = #{inputVersion}, plan_version = #{planVersion},
                version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
            WHERE interview_id = #{interviewId} AND owner_id = #{ownerId}
              AND version = #{expectedVersion}
              AND session_stage IN ('PREPARING', 'READY')
            """)
    int reconfigureBeforeStart(@Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("expectedVersion") long expectedVersion,
            @Param("inputVersion") long inputVersion,
            @Param("planVersion") long planVersion,
            @Param("durationSeconds") long durationSeconds);
}
