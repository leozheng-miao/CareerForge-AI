package com.leo.careerforgeai.interview.infrastructure.persistence.entity;

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
 * @description: 映射mock_interview_session表中的身份、冻结输入、预算、状态和版本
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("mock_interview_session")
public class MockInterviewSessionEntity {

    @TableId(value = "interview_id", type = IdType.INPUT)
    private String interviewId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("request_id")
    private String requestId;

    @TableField("request_fingerprint")
    private String requestFingerprint;

    @TableField("input_snapshot_id")
    private String inputSnapshotId;

    @TableField("input_snapshot_hash")
    private String inputSnapshotHash;

    @TableField("interview_mode")
    private String interviewMode;

    @TableField("interview_status")
    private String interviewStatus;

    @TableField("max_questions")
    private Integer maxQuestions;

    @TableField("max_follow_ups")
    private Integer maxFollowUps;

    @TableField("max_model_calls")
    private Integer maxModelCalls;

    @TableField("max_total_tokens")
    private Long maxTotalTokens;

    @TableField("failure_code")
    private String failureCode;

    @TableField("version")
    private Long version;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableField("finished_at")
    private Instant finishedAt;
}