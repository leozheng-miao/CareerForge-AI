package com.leo.careerforgeai.career.infrastructure.persistence.entity;

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
 * @description: 映射training_plan表并保存训练计划状态、版本和业务时间
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("training_plan")
public class TrainingPlanEntity {

    @TableId(value = "plan_id", type = IdType.INPUT)
    private String planId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("plan_version")
    private Long planVersion;

    @TableField("gap_snapshot_id")
    private String gapSnapshotId;

    /** 生成期固定输入、资源版本和模型调用审计JSON。 */
    @TableField("generation_context_json")
    private String generationContextJson;

    @TableField("title")
    private String title;

    @TableField("plan_status")
    private String planStatus;

    @TableField("version")
    private Long version;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableField("activated_at")
    private Instant activatedAt;

    @TableField("completed_at")
    private Instant completedAt;

    @TableField("cancelled_at")
    private Instant cancelledAt;
}