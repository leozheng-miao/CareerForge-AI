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
 * @description: 映射mock_interview_input_snapshot冻结输入主表
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("mock_interview_input_snapshot")
public class MockInterviewInputSnapshotEntity {

    @TableId(value = "input_snapshot_id", type = IdType.INPUT)
    private String inputSnapshotId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("schema_version")
    private Integer schemaVersion;

    @TableField("target_role_id")
    private String targetRoleId;

    @TableField("target_role_version")
    private Long targetRoleVersion;

    @TableField("skill_gap_snapshot_id")
    private String skillGapSnapshotId;

    @TableField("training_plan_id")
    private String trainingPlanId;

    @TableField("training_plan_version")
    private Long trainingPlanVersion;

    @TableField("snapshot_context_json")
    private String snapshotContextJson;

    @TableField("snapshot_hash")
    private String snapshotHash;

    @TableField("created_at")
    private Instant createdAt;
}