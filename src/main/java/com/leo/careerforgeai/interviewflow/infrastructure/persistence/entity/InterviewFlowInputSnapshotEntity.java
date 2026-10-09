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
 * @description: 映射interview_flow_input_snapshot面试确认输入快照
 * @author: Miao Zheng
 * @date: 2026-10-02
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("interview_flow_input_snapshot")
public class InterviewFlowInputSnapshotEntity {

    @TableId(value = "input_snapshot_id", type = IdType.INPUT)
    private String inputSnapshotId;

    @TableField("session_id")
    private String interviewId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("interview_direction")
    private String interviewDirection;

    @TableField("job_description")
    private String jobDescription;

    @TableField("salary_range")
    private String salaryRange;

    @TableField("personal_description")
    private String personalDescription;

    @TableField("confirmed_profile_json")
    private String confirmedProfileJson;

    @TableField("profile_schema_version")
    private Integer profileSchemaVersion;

    @TableField("difficulty")
    private String difficulty;

    @TableField("duration_seconds")
    private Long durationSeconds;

    @TableField("input_version")
    private Long inputVersion;

    @TableField("version")
    private Long version;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
