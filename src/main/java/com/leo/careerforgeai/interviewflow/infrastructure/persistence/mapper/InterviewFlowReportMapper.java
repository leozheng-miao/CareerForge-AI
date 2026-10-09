package com.leo.careerforgeai.interviewflow.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity.InterviewFlowReportEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @program: CareerForge-AI
 * @description: 持久化一场面试唯一的异步正式报告
 * @author: Miao Zheng
 * @date: 2026-10-03
 **/
@Mapper
public interface InterviewFlowReportMapper
        extends BaseMapper<InterviewFlowReportEntity> {

    @Select("""
            SELECT report_id AS reportId, session_id AS interviewId,
                   owner_id AS ownerId, report_status AS reportStatus,
                   report_json AS reportJson,
                   schema_version AS schemaVersion,
                   input_version AS inputVersion,
                   plan_version AS planVersion,
                   context_fingerprint AS contextFingerprint,
                   failure_code AS failureCode, version,
                   created_at AS createdAt, updated_at AS updatedAt,
                   completed_at AS completedAt
            FROM interview_flow_report
            WHERE owner_id = #{ownerId}
              AND session_id = #{interviewId}
            """)
    InterviewFlowReportEntity selectByInterview(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId
    );

    @Update("""
            UPDATE interview_flow_report
            SET report_status = #{report.reportStatus},
                report_json = #{report.reportJson},
                schema_version = #{report.schemaVersion},
                input_version = #{report.inputVersion},
                plan_version = #{report.planVersion},
                context_fingerprint = #{report.contextFingerprint},
                failure_code = #{report.failureCode},
                version = #{report.version},
                updated_at = #{report.updatedAt},
                completed_at = #{report.completedAt}
            WHERE report_id = #{report.reportId}
              AND session_id = #{report.interviewId}
              AND owner_id = #{ownerId}
              AND version = #{expectedVersion}
            """)
    int updateIfVersionMatches(
            @Param("report") InterviewFlowReportEntity report,
            @Param("ownerId") String ownerId,
            @Param("expectedVersion") long expectedVersion
    );
}
