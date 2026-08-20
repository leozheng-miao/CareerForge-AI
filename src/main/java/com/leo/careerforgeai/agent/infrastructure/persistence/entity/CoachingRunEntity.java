package com.leo.careerforgeai.agent.infrastructure.persistence.entity;

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
 * @description: 映射coaching_run表中的耐久请求身份、状态、Turn引用和乐观锁版本
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("coaching_run")
public class CoachingRunEntity {

    @TableId(value = "run_id", type = IdType.INPUT)
    private String runId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("session_id")
    private String sessionId;

    @TableField("request_id")
    private String requestId;

    @TableField("request_fingerprint")
    private String requestFingerprint;

    @TableField("expected_session_version")
    private Long expectedSessionVersion;

    @TableField("run_status")
    private String runStatus;

    @TableField("user_turn_id")
    private String userTurnId;

    @TableField("assistant_turn_id")
    private String assistantTurnId;

    @TableField("failure_code")
    private String failureCode;

    @TableField("version")
    private Long version;

    @TableField("accepted_at")
    private Instant acceptedAt;

    @TableField("started_at")
    private Instant startedAt;

    @TableField("finished_at")
    private Instant finishedAt;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}