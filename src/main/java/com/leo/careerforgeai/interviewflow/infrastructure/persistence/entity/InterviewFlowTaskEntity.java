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
 * @description: 映射interview_flow_task异步任务、重试和租约事实
 * @author: Miao Zheng
 * @date: 2026-10-02
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("interview_flow_task")
public class InterviewFlowTaskEntity {

    @TableId(value = "task_id", type = IdType.INPUT)
    private String taskId;

    @TableField("session_id")
    private String interviewId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("task_type")
    private String taskType;

    @TableField("business_key")
    private String businessKey;

    @TableField("task_status")
    private String taskStatus;

    @TableField("attempt_count")
    private Integer attemptCount;

    @TableField("max_attempts")
    private Integer maxAttempts;

    @TableField("lease_owner")
    private String leaseOwner;

    @TableField("lease_until")
    private Instant leaseUntil;

    @TableField("next_retry_at")
    private Instant nextRetryAt;

    @TableField("input_version")
    private Long inputVersion;

    @TableField("plan_version")
    private Long planVersion;

    @TableField("context_fingerprint")
    private String contextFingerprint;

    @TableField("last_error_code")
    private String lastErrorCode;

    @TableField("last_error_message")
    private String lastErrorMessage;

    @TableField("started_at")
    private Instant startedAt;

    @TableField("completed_at")
    private Instant completedAt;

    @TableField("version")
    private Long version;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
