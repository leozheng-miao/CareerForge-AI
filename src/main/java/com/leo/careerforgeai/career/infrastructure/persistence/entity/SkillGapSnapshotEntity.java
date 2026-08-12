
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
 * @description: 映射skill_gap_snapshot表并保存固定目标和画像版本下的能力差距快照
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("skill_gap_snapshot")
public class SkillGapSnapshotEntity {

    @TableId(value = "snapshot_id", type = IdType.INPUT)
    private String snapshotId;

    @TableField("owner_id")
    private String ownerId;

    @TableField("target_role_id")
    private String targetRoleId;

    @TableField("target_role_version")
    private Long targetRoleVersion;

    @TableField("profile_version")
    private Long profileVersion;

    /** GapItem列表的JSON快照。 */
    @TableField("items_json")
    private String itemsJson;

    @TableField("created_at")
    private Instant createdAt;
}