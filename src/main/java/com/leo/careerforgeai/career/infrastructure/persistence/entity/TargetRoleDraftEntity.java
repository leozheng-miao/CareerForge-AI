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
 * @description: 映射target_role_draft表并保存等待用户确认的岗位草案
 * @author: Miao Zheng
 * @date: 2026-08-16
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("target_role_draft")
public class TargetRoleDraftEntity {

    @TableId(value = "draft_id", type = IdType.INPUT)
    private String draftId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("source_ref")
    private String sourceRef;

    @TableField("source_hash")
    private String sourceHash;

    @TableField("parser_version")
    private String parserVersion;

    @TableField("prompt_version")
    private String promptVersion;

    @TableField("requirements_json")
    private String requirementsJson;

    @TableField("draft_status")
    private String draftStatus;

    @TableField("version")
    private Long version;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("confirmed_target_role_id")
    private String confirmedTargetRoleId;

    @TableField("confirmed_target_role_version")
    private Long confirmedTargetRoleVersion;

    @TableField("confirmed_at")
    private Instant confirmedAt;
}