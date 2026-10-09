package com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowTaskEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 幂等入队并原子认领待执行或租约过期的面试任务
 * @author: Miao Zheng
 * @date: 2026-10-03
 **/
@Mapper
public interface InterviewFlowTaskMapper
        extends BaseMapper<InterviewFlowTaskEntity> {

    @Insert("""
            INSERT INTO interview_flow_task (
                task_id, session_id, owner_id, task_type, business_key,
                task_status, attempt_count, max_attempts,
                lease_owner, lease_until, next_retry_at,
                input_version, plan_version, context_fingerprint,
                last_error_code, last_error_message,
                started_at, completed_at, version, created_at, updated_at
            )
            VALUES (
                #{task.taskId}, #{task.interviewId}, #{task.ownerId},
                #{task.taskType}, #{task.businessKey}, #{task.taskStatus},
                #{task.attemptCount}, #{task.maxAttempts},
                #{task.leaseOwner}, #{task.leaseUntil},
                #{task.nextRetryAt}, #{task.inputVersion},
                #{task.planVersion}, #{task.contextFingerprint},
                #{task.lastErrorCode}, #{task.lastErrorMessage},
                #{task.startedAt}, #{task.completedAt}, #{task.version},
                #{task.createdAt}, #{task.updatedAt}
            )
            ON DUPLICATE KEY UPDATE task_id = task_id
            """)
    int enqueue(@Param("task") InterviewFlowTaskEntity task);

    @Update("""
            UPDATE interview_flow_task
            SET task_status = 'RUNNING',
                attempt_count = attempt_count + 1,
                lease_owner = #{leaseOwner},
                lease_until = #{leaseUntil},
                next_retry_at = NULL,
                started_at = COALESCE(started_at, #{now}),
                version = version + 1,
                updated_at = #{now}
            WHERE attempt_count < max_attempts
              AND (
                    task_status = 'PENDING'
                    OR (
                        task_status = 'RETRY_WAIT'
                        AND next_retry_at <= #{now}
                    )
                    OR (
                        task_status = 'RUNNING'
                        AND lease_until <= #{now}
                    )
              )
            ORDER BY created_at ASC, task_id ASC
            LIMIT 1
            """)
    int claimNextAvailable(
            @Param("now") Instant now,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseUntil") Instant leaseUntil
    );

    @Select("""
            SELECT task_id AS taskId, session_id AS interviewId,
                   owner_id AS ownerId, task_type AS taskType,
                   business_key AS businessKey,
                   task_status AS taskStatus,
                   attempt_count AS attemptCount,
                   max_attempts AS maxAttempts,
                   lease_owner AS leaseOwner,
                   lease_until AS leaseUntil,
                   next_retry_at AS nextRetryAt,
                   input_version AS inputVersion,
                   plan_version AS planVersion,
                   context_fingerprint AS contextFingerprint,
                   last_error_code AS lastErrorCode,
                   last_error_message AS lastErrorMessage,
                   started_at AS startedAt,
                   completed_at AS completedAt, version,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM interview_flow_task
            WHERE task_status = 'RUNNING'
              AND lease_owner = #{leaseOwner}
            ORDER BY updated_at DESC, task_id DESC
            LIMIT 1
            """)
    InterviewFlowTaskEntity selectClaimedByLeaseOwner(
            @Param("leaseOwner") String leaseOwner
    );

    @Update("""
            UPDATE interview_flow_task
            SET task_status = 'SUCCEEDED',
                lease_owner = NULL,
                lease_until = NULL,
                next_retry_at = NULL,
                last_error_code = NULL,
                last_error_message = NULL,
                completed_at = #{completedAt},
                version = version + 1,
                updated_at = #{completedAt}
            WHERE task_id = #{taskId}
              AND owner_id = #{ownerId}
              AND task_status = 'RUNNING'
              AND lease_owner = #{leaseOwner}
              AND version = #{expectedVersion}
            """)
    int markSucceeded(
            @Param("taskId") String taskId,
            @Param("ownerId") String ownerId,
            @Param("leaseOwner") String leaseOwner,
            @Param("expectedVersion") long expectedVersion,
            @Param("completedAt") Instant completedAt
    );

    @Update("""
            UPDATE interview_flow_task
            SET task_status = 'RETRY_WAIT',
                lease_owner = NULL,
                lease_until = NULL,
                next_retry_at = #{nextRetryAt},
                last_error_code = #{errorCode},
                last_error_message = #{errorMessage},
                version = version + 1,
                updated_at = #{updatedAt}
            WHERE task_id = #{taskId}
              AND owner_id = #{ownerId}
              AND task_status = 'RUNNING'
              AND lease_owner = #{leaseOwner}
              AND version = #{expectedVersion}
            """)
    int markRetryWait(
            @Param("taskId") String taskId,
            @Param("ownerId") String ownerId,
            @Param("leaseOwner") String leaseOwner,
            @Param("expectedVersion") long expectedVersion,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("updatedAt") Instant updatedAt
    );

    @Update("""
            UPDATE interview_flow_task
            SET task_status = 'RETRY_WAIT',
                attempt_count = attempt_count - 1,
                lease_owner = NULL,
                lease_until = NULL,
                next_retry_at = #{nextRetryAt},
                last_error_code = #{errorCode},
                last_error_message = #{errorMessage},
                version = version + 1,
                updated_at = #{updatedAt}
            WHERE task_id = #{taskId}
              AND owner_id = #{ownerId}
              AND task_status = 'RUNNING'
              AND lease_owner = #{leaseOwner}
              AND version = #{expectedVersion}
              AND attempt_count > 0
            """)
    int rescheduleUnstarted(
            @Param("taskId") String taskId,
            @Param("ownerId") String ownerId,
            @Param("leaseOwner") String leaseOwner,
            @Param("expectedVersion") long expectedVersion,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("updatedAt") Instant updatedAt
    );

    @Update("""
            UPDATE interview_flow_task
            SET task_status = 'FAILED',
                lease_owner = NULL,
                lease_until = NULL,
                next_retry_at = NULL,
                last_error_code = #{errorCode},
                last_error_message = #{errorMessage},
                completed_at = #{completedAt},
                version = version + 1,
                updated_at = #{completedAt}
            WHERE task_id = #{taskId}
              AND owner_id = #{ownerId}
              AND task_status = 'RUNNING'
              AND lease_owner = #{leaseOwner}
              AND version = #{expectedVersion}
            """)
    int markFailed(
            @Param("taskId") String taskId,
            @Param("ownerId") String ownerId,
            @Param("leaseOwner") String leaseOwner,
            @Param("expectedVersion") long expectedVersion,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("completedAt") Instant completedAt
    );
}
