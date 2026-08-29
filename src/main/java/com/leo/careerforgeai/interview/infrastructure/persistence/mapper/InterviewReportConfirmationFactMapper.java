package com.leo.careerforgeai.interview.infrastructure.persistence.mapper;

import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewReportConfirmationPersistenceModels.ConfirmationRow;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewReportConfirmationPersistenceModels.DecisionRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 幂等保存、查询并CAS更新报告确认单及逐项决定
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Mapper
public interface InterviewReportConfirmationFactMapper {

    @Insert("""
            INSERT INTO interview_report_confirmation (
                confirmation_id, report_id, interview_id, owner_id,
                request_id, request_fingerprint, expected_report_version,
                confirmation_status, failure_code, version,
                created_at, updated_at, application_finished_at
            )
            VALUES (
                #{confirmation.confirmationId}, #{confirmation.reportId},
                #{confirmation.interviewId}, #{confirmation.ownerId},
                #{confirmation.requestId}, #{confirmation.requestFingerprint},
                #{confirmation.expectedReportVersion},
                #{confirmation.confirmationStatus}, #{confirmation.failureCode},
                #{confirmation.version}, #{confirmation.createdAt},
                #{confirmation.updatedAt}, #{confirmation.applicationFinishedAt}
            )
            ON DUPLICATE KEY UPDATE confirmation_id = confirmation_id
            """)
    int claimConfirmation(@Param("confirmation") ConfirmationRow confirmation);

    @Insert("""
            <script>
            INSERT INTO interview_report_decision (
                decision_id, confirmation_id, suggestion_id,
                report_id, interview_id, owner_id,
                decision_type, application_status,
                output_reference_id, failure_code,
                created_at, updated_at, finished_at
            )
            VALUES
            <foreach collection="decisions" item="decision" separator=",">
                (
                    #{decision.decisionId}, #{decision.confirmationId},
                    #{decision.suggestionId}, #{decision.reportId},
                    #{decision.interviewId}, #{decision.ownerId},
                    #{decision.decisionType}, #{decision.applicationStatus},
                    #{decision.outputReferenceId}, #{decision.failureCode},
                    #{decision.createdAt}, #{decision.updatedAt},
                    #{decision.finishedAt}
                )
            </foreach>
            ON DUPLICATE KEY UPDATE decision_id = decision_id
            </script>
            """)
    int claimDecisions(@Param("decisions") List<DecisionRow> decisions);

    @Select("""
            SELECT confirmation_id AS confirmationId, report_id AS reportId,
                   interview_id AS interviewId, owner_id AS ownerId,
                   request_id AS requestId,
                   request_fingerprint AS requestFingerprint,
                   expected_report_version AS expectedReportVersion,
                   confirmation_status AS confirmationStatus,
                   failure_code AS failureCode, version,
                   created_at AS createdAt, updated_at AS updatedAt,
                   application_finished_at AS applicationFinishedAt
            FROM interview_report_confirmation
            WHERE owner_id = #{ownerId}
              AND request_id = #{requestId}
            """)
    ConfirmationRow findByRequest(
            @Param("ownerId") String ownerId,
            @Param("requestId") String requestId
    );

    @Select("""
            SELECT confirmation_id AS confirmationId, report_id AS reportId,
                   interview_id AS interviewId, owner_id AS ownerId,
                   request_id AS requestId,
                   request_fingerprint AS requestFingerprint,
                   expected_report_version AS expectedReportVersion,
                   confirmation_status AS confirmationStatus,
                   failure_code AS failureCode, version,
                   created_at AS createdAt, updated_at AS updatedAt,
                   application_finished_at AS applicationFinishedAt
            FROM interview_report_confirmation
            WHERE owner_id = #{ownerId}
              AND interview_id = #{interviewId}
              AND report_id = #{reportId}
            """)
    ConfirmationRow findByReport(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("reportId") String reportId
    );

    @Select("""
            SELECT decision_id AS decisionId,
                   confirmation_id AS confirmationId,
                   suggestion_id AS suggestionId,
                   report_id AS reportId, interview_id AS interviewId,
                   owner_id AS ownerId, decision_type AS decisionType,
                   application_status AS applicationStatus,
                   output_reference_id AS outputReferenceId,
                   failure_code AS failureCode,
                   created_at AS createdAt, updated_at AS updatedAt,
                   finished_at AS finishedAt
            FROM interview_report_decision
            WHERE owner_id = #{ownerId}
              AND confirmation_id = #{confirmationId}
              AND report_id = #{reportId}
              AND interview_id = #{interviewId}
            ORDER BY decision_id
            """)
    List<DecisionRow> findDecisions(
            @Param("ownerId") String ownerId,
            @Param("confirmationId") String confirmationId,
            @Param("reportId") String reportId,
            @Param("interviewId") String interviewId
    );

    @Update("""
            UPDATE interview_report_confirmation
            SET confirmation_status = #{confirmation.confirmationStatus},
                failure_code = #{confirmation.failureCode},
                version = #{confirmation.version},
                updated_at = #{confirmation.updatedAt},
                application_finished_at = #{confirmation.applicationFinishedAt}
            WHERE confirmation_id = #{confirmation.confirmationId}
              AND report_id = #{confirmation.reportId}
              AND interview_id = #{confirmation.interviewId}
              AND owner_id = #{confirmation.ownerId}
              AND version = #{expectedVersion}
            """)
    int updateConfirmationIfVersionMatches(
            @Param("confirmation") ConfirmationRow confirmation,
            @Param("expectedVersion") long expectedVersion
    );

    @Update("""
            UPDATE interview_report_decision
            SET application_status = #{decision.applicationStatus},
                output_reference_id = #{decision.outputReferenceId},
                failure_code = #{decision.failureCode},
                updated_at = #{decision.updatedAt},
                finished_at = #{decision.finishedAt}
            WHERE decision_id = #{decision.decisionId}
              AND confirmation_id = #{decision.confirmationId}
              AND suggestion_id = #{decision.suggestionId}
              AND report_id = #{decision.reportId}
              AND interview_id = #{decision.interviewId}
              AND owner_id = #{decision.ownerId}
            """)
    int updateDecision(@Param("decision") DecisionRow decision);
}