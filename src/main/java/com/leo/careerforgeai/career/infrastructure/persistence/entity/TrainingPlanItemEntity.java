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
 * @description: 映射training_plan_item表并保存任务内容、引用、进度和完成证据
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("training_plan_item")
public class TrainingPlanItemEntity {

    @TableId(value = "item_id", type = IdType.INPUT)
    private String itemId;

    @TableField("plan_id")
    private String planId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("week_number")
    private Integer weekNumber;

    @TableField("title")
    private String title;

    @TableField("task_description")
    private String taskDescription;

    @TableField("estimated_minutes")
    private Integer estimatedMinutes;

    @TableField("completion_criteria")
    private String completionCriteria;

    @TableField("evidence_requirement")
    private String evidenceRequirement;

    /** 关联GapItem UUID列表的JSON数组。 */
    @TableField("gap_item_ids_json")
    private String gapItemIdsJson;

    @TableField("foundation_goal")
    private String foundationGoal;

    /** 类型化ResourceRef列表的JSON数组。 */
    @TableField("resource_refs_json")
    private String resourceRefsJson;

    @TableField("item_status")
    private String itemStatus;

    /** 用户实际提交的完成证据引用JSON数组。 */
    @TableField("completion_evidence_refs_json")
    private String completionEvidenceRefsJson;

    @TableField("version")
    private Long version;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}