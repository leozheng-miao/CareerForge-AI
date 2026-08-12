package com.leo.careerforgeai.memory.infrastructure.persistence.entity;

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
 * @description: 映射memory_decision表并保存每次Memory状态变化的用户决策审计记录
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("memory_decision")
public class MemoryDecisionEntity {

    /** 服务端生成的决策审计UUID。 */
    @TableId(value = "decision_id", type = IdType.INPUT)
    private String decisionId;

    /** 被操作的Memory ID。 */
    @TableField("memory_id")
    private String memoryId;

    /** Memory所属用户，同时用于查询隔离。 */
    @TableField("owner_id")
    private String ownerId;

    /** 用户执行的确认、拒绝、替代或撤销动作。 */
    @TableField("decision_type")
    private String decisionType;

    /** 决策执行前的Memory状态。 */
    @TableField("from_status")
    private String fromStatus;

    /** 决策执行后的Memory状态。 */
    @TableField("to_status")
    private String toStatus;

    /** 用户作出决策时看到的Memory版本。 */
    @TableField("expected_memory_version")
    private Long expectedMemoryVersion;

    /** 替代操作对应的新Memory ID，其他操作为空。 */
    @TableField("replacement_memory_id")
    private String replacementMemoryId;

    /** 用户提供的可选决策说明。 */
    @TableField("note")
    private String note;

    /** 服务端记录的决策时间。 */
    @TableField("decided_at")
    private Instant decidedAt;
}