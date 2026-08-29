package com.leo.careerforgeai.interview.infrastructure.persistence.mapper;

import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewReportPersistenceModels.ReportRow;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewReportPersistenceModels.SuggestionRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 幂等保存并按owner隔离查询面试报告及有序建议
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Mapper
public interface InterviewReportFactMapper {

    @Insert("""
            INSERT INTO interview_report (
                report_id, interview_id, owner_id, report_version,
                report_status, strengths_json, technical_gaps_json,
                evidence_expression_risks_json, improvement_actions_json,
                model_request_id, prompt_version, input_hash, output_hash,
                version, created_at, updated_at, decided_at
            )
            VALUES (
                #{report.reportId}, #{report.interviewId}, #{report.ownerId},
                #{report.reportVersion}, #{report.reportStatus},
                #{report.strengthsJson}, #{report.technicalGapsJson},
                #{report.evidenceExpressionRisksJson}, #{report.improvementActionsJson},
                #{report.modelRequestId}, #{report.promptVersion},
                #{report.inputHash}, #{report.outputHash}, #{report.version},
                #{report.createdAt}, #{report.updatedAt}, #{report.decidedAt}
            )
            ON DUPLICATE KEY UPDATE report_id = report_id
            """)
    int claimReport(@Param("report") ReportRow report);

    @Insert("""
            <script>
            INSERT INTO interview_report_suggestion (
                suggestion_id, report_id, interview_id, owner_id,
                suggestion_type, suggestion_order, suggestion_content,
                suggestion_payload_json, content_hash, created_at
            )
            VALUES
            <foreach collection="suggestions" item="suggestion" separator=",">
                (
                    #{suggestion.suggestionId}, #{suggestion.reportId},
                    #{suggestion.interviewId}, #{suggestion.ownerId},
                    #{suggestion.suggestionType}, #{suggestion.suggestionOrder},
                    #{suggestion.suggestionContent}, #{suggestion.suggestionPayloadJson},
                    #{suggestion.contentHash}, #{suggestion.createdAt}
                )
            </foreach>
            ON DUPLICATE KEY UPDATE suggestion_id = suggestion_id
            </script>
            """)
    int claimSuggestions(@Param("suggestions") List<SuggestionRow> suggestions);

    @Select("""
            SELECT report_id AS reportId, interview_id AS interviewId,
                   owner_id AS ownerId, report_version AS reportVersion,
                   report_status AS reportStatus, strengths_json AS strengthsJson,
                   technical_gaps_json AS technicalGapsJson,
                   evidence_expression_risks_json AS evidenceExpressionRisksJson,
                   improvement_actions_json AS improvementActionsJson,
                   model_request_id AS modelRequestId,
                   prompt_version AS promptVersion,
                   input_hash AS inputHash, output_hash AS outputHash,
                   version, created_at AS createdAt,
                   updated_at AS updatedAt, decided_at AS decidedAt
            FROM interview_report
            WHERE owner_id = #{ownerId}
              AND interview_id = #{interviewId}
            ORDER BY report_version DESC
            LIMIT 1
            """)
    ReportRow findByInterview(@Param("ownerId") String ownerId, @Param("interviewId") String interviewId);

    @Select("""
            SELECT report_id AS reportId, interview_id AS interviewId,
                   owner_id AS ownerId, report_version AS reportVersion,
                   report_status AS reportStatus, strengths_json AS strengthsJson,
                   technical_gaps_json AS technicalGapsJson,
                   evidence_expression_risks_json AS evidenceExpressionRisksJson,
                   improvement_actions_json AS improvementActionsJson,
                   model_request_id AS modelRequestId,
                   prompt_version AS promptVersion,
                   input_hash AS inputHash, output_hash AS outputHash,
                   version, created_at AS createdAt,
                   updated_at AS updatedAt, decided_at AS decidedAt
            FROM interview_report
            WHERE owner_id = #{ownerId}
              AND interview_id = #{interviewId}
              AND report_id = #{reportId}
            """)
    ReportRow findById(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("reportId") String reportId
    );

    @Select("""
        SELECT suggestion_id AS suggestionId, report_id AS reportId,
               interview_id AS interviewId, owner_id AS ownerId,
               suggestion_type AS suggestionType,
               suggestion_order AS suggestionOrder,
               suggestion_content AS suggestionContent,
               suggestion_payload_json AS suggestionPayloadJson,
               content_hash AS contentHash, created_at AS createdAt
        FROM interview_report_suggestion
        WHERE owner_id = #{ownerId}
          AND interview_id = #{interviewId}
          AND report_id = #{reportId}
        ORDER BY suggestion_type, suggestion_order
        """)
    List<SuggestionRow> findSuggestions(
            @Param("ownerId") String ownerId,
            @Param("interviewId") String interviewId,
            @Param("reportId") String reportId
    );

    @Update("""
            UPDATE interview_report
            SET report_status = #{report.reportStatus},
                version = #{report.version},
                updated_at = #{report.updatedAt},
                decided_at = #{report.decidedAt}
            WHERE report_id = #{report.reportId}
              AND interview_id = #{report.interviewId}
              AND owner_id = #{report.ownerId}
              AND version = #{expectedVersion}
            """)
    int updateIfVersionMatches(
            @Param("report") ReportRow report,
            @Param("expectedVersion") long expectedVersion
    );
}