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
 * @description: 映射target_role表并保存用户确认后的目标岗位冻结版本
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("target_role")
public class TargetRoleEntity {

    @TableId(value = "target_role_id", type = IdType.INPUT)
    private String targetRoleId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("target_role_version")
    private Long targetRoleVersion;

    @TableField("source_ref")
    private String sourceRef;

    @TableField("source_hash")
    private String sourceHash;

    @TableField("parser_version")
    private String parserVersion;

    @TableField("prompt_version")
    private String promptVersion;

    /** 用户确认后冻结的JobRequirements JSON快照。 */
    @TableField("requirements_json")
    private String requirementsJson;

    @TableField("confirmed_at")
    private Instant confirmedAt;
}