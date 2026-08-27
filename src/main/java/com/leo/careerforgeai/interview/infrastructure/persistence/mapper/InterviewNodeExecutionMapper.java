package com.leo.careerforgeai.interview.infrastructure.persistence.mapper;

import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewNodeExecutionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 认领、查询并CAS更新Graph节点执行记录
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Mapper
public interface InterviewNodeExecutionMapper {

    @Insert("""
            INSERT INTO interview_node_execution (
                execution_id, interview_id, owner_id, round_no,
                node_name, input_hash, execution_status,
                output_reference_id, model_request_id,
                attempt_count, model_call_count,
                input_tokens, output_tokens, total_tokens,
                model_duration_ms, failure_code, version,
                started_at, finished_at, created_at, updated_at
            )
            VALUES (
                #{execution.executionId}, #{execution.interviewId},
                #{execution.ownerId}, #{execution.roundNo},
                #{execution.nodeName}, #{execution.inputHash},
                #{execution.executionStatus}, #{execution.outputReferenceId},
                #{execution.modelRequestId}, #{execution.attemptCount},
                #{execution.modelCallCount}, #{execution.inputTokens},
                #{execution.outputTokens}, #{execution.totalTokens},
                #{execution.modelDurationMs}, #{execution.failureCode},
                #{execution.version}, #{execution.startedAt},
                #{execution.finishedAt}, #{execution.createdAt},
                #{execution.updatedAt}
            )
            ON DUPLICATE KEY UPDATE execution_id = execution_id
            """)
    int claim(@Param("execution") InterviewNodeExecutionEntity execution);

    @Select("""
            SELECT execution_id AS executionId, interview_id AS interviewId,
                   owner_id AS ownerId, round_no AS roundNo,
                   node_name AS nodeName, input_hash AS inputHash,
                   execution_status AS executionStatus,
                   output_reference_id AS outputReferenceId,
                   model_request_id AS modelRequestId,
                   attempt_count AS attemptCount,
                   model_call_count AS modelCallCount,
                   input_tokens AS inputTokens, output_tokens AS outputTokens,
                   total_tokens AS totalTokens,
                   model_duration_ms AS modelDurationMs,
                   failure_code AS failureCode, version,
                   started_at AS startedAt, finished_at AS finishedAt,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM interview_node_execution
            WHERE owner_id = #{ownerId}
              AND interview_id = #{interviewId}
              AND execution_id = #{executionId}
            """)
    InterviewNodeExecutionEntity findById(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("executionId") String executionId
    );

    @Select("""
            SELECT execution_id AS executionId, interview_id AS interviewId,
                   owner_id AS ownerId, round_no AS roundNo,
                   node_name AS nodeName, input_hash AS inputHash,
                   execution_status AS executionStatus,
                   output_reference_id AS outputReferenceId,
                   model_request_id AS modelRequestId,
                   attempt_count AS attemptCount,
                   model_call_count AS modelCallCount,
                   input_tokens AS inputTokens, output_tokens AS outputTokens,
                   total_tokens AS totalTokens,
                   model_duration_ms AS modelDurationMs,
                   failure_code AS failureCode, version,
                   started_at AS startedAt, finished_at AS finishedAt,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM interview_node_execution
            WHERE owner_id = #{ownerId}
              AND interview_id = #{interviewId}
              AND round_no = #{roundNo}
              AND node_name = #{nodeName}
              AND input_hash = #{inputHash}
            """)
    InterviewNodeExecutionEntity findByIdentity(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("roundNo") int roundNo,
            @Param("nodeName") String nodeName,
            @Param("inputHash") String inputHash
    );

    @Update("""
            UPDATE interview_node_execution
            SET execution_status = #{executionStatus},
                output_reference_id = #{outputReferenceId},
                model_request_id = #{modelRequestId},
                attempt_count = #{attemptCount},
                model_call_count = #{modelCallCount},
                input_tokens = #{inputTokens},
                output_tokens = #{outputTokens},
                total_tokens = #{totalTokens},
                model_duration_ms = #{modelDurationMs},
                failure_code = #{failureCode},
                version = #{newVersion},
                started_at = #{startedAt},
                finished_at = #{finishedAt},
                updated_at = #{updatedAt}
            WHERE execution_id = #{executionId}
              AND interview_id = #{interviewId}
              AND owner_id = #{ownerId}
              AND version = #{expectedVersion}
            """)
    int updateIfVersionMatches(
            @Param("executionId") String executionId,
            @Param("interviewId") String interviewId,
            @Param("ownerId") String ownerId,
            @Param("executionStatus") String executionStatus,
            @Param("outputReferenceId") String outputReferenceId,
            @Param("modelRequestId") String modelRequestId,
            @Param("attemptCount") int attemptCount,
            @Param("modelCallCount") int modelCallCount,
            @Param("inputTokens") long inputTokens,
            @Param("outputTokens") long outputTokens,
            @Param("totalTokens") long totalTokens,
            @Param("modelDurationMs") long modelDurationMs,
            @Param("failureCode") String failureCode,
            @Param("newVersion") long newVersion,
            @Param("startedAt") Instant startedAt,
            @Param("finishedAt") Instant finishedAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("expectedVersion") long expectedVersion
    );
}