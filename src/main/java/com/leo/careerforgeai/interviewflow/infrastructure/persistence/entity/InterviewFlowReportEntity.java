package com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * @program: CareerForge-AI
 * @description: 映射interview_flow_report异步正式报告及其输入版本
 * @author: Miao Zheng
 * @date: 2026-10-02
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("interview_flow_report")
public class InterviewFlowReportEntity {

    @TableId(value = "report_id", type = IdType.INPUT)
    private String reportId;

    @TableField("session_id")
    private String interviewId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("report_status")
    private String reportStatus;

    @TableField("report_json")
    private String reportJson;

    @TableField("schema_version")
    private Integer schemaVersion;

    @TableField("input_version")
    private Long inputVersion;

    @TableField("plan_version")
    private Long planVersion;

    @TableField("context_fingerprint")
    private String contextFingerprint;

    @TableField("failure_code")
    private String failureCode;

    @TableField("version")
    private Long version;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableField("completed_at")
    private Instant completedAt;
}
