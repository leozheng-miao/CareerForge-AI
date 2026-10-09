package com.leo.careerforgeai.interviewflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
 * @description: 映射interview_flow_session面试流程事实、计时和乐观锁版本
 * @author: Miao Zheng
 * @date: 2026-10-02
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("interview_flow_session")
public class InterviewFlowSessionEntity {

    @TableId(value = "interview_id", type = IdType.INPUT)
    private String interviewId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("request_id")
    private String requestId;

    @TableField("request_fingerprint")
    private String requestFingerprint;

    @TableField("interview_mode")
    private String interviewMode;

    @TableField("session_stage")
    private String sessionStage;

    @TableField("timer_state")
    private String timerState;

    @TableField("duration_seconds")
    private Long durationSeconds;

    @TableField("accumulated_answer_seconds")
    private Long accumulatedAnswerSeconds;

    @TableField("last_active_at")
    private Instant lastActiveAt;

    @TableField("current_topic_id")
    private String currentTopicId;

    @TableField("current_question_id")
    private String currentQuestionId;

    @TableField("submitted_answer_count")
    private Integer submittedAnswerCount;

    @TableField("follow_up_count")
    private Integer followUpCount;

    @TableField("consecutive_follow_up_count")
    private Integer consecutiveFollowUpCount;

    @TableField("input_version")
    private Long inputVersion;

    @TableField("plan_version")
    private Long planVersion;

    @TableField("version")
    private Long version;

    @TableField("started_at")
    private Instant startedAt;

    @TableField("finished_at")
    private Instant finishedAt;

    @TableField("failure_code")
    private String failureCode;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableField(
            value = "active_owner_id",
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER
    )
    private String activeOwnerId;
}
