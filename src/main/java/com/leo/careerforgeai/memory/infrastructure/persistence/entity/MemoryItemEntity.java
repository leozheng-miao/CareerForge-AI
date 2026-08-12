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
 * @description: 映射memory_item表并保存Memory当前状态、用户归属和来源信息
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("memory_item")
public class MemoryItemEntity {

    /** 服务端生成的UUID字符串，不使用数据库自增主键。 */
    @TableId(value = "memory_id", type = IdType.INPUT)
    private String memoryId;

    /** 数据所属用户，所有读取和更新必须携带该条件。 */
    @TableField("owner_id")
    private String ownerId;

    /** Memory业务类型，对应MemoryType枚举名称。 */
    @TableField("memory_type")
    private String memoryType;

    /** 冲突槽位或技能分组键。 */
    @TableField("normalized_key")
    private String normalizedKey;

    /** 生成normalizedKey时使用的规则版本。 */
    @TableField("normalization_version")
    private String normalizationVersion;

    /** 经过领域校验的Memory正文。 */
    @TableField("content")
    private String content;

    /** Memory正文的小写SHA-256。 */
    @TableField("content_hash")
    private String contentHash;

    /** 当前生命周期状态，对应MemoryStatus枚举名称。 */
    @TableField("memory_status")
    private String memoryStatus;

    /** Memory来源类型，对应MemorySourceType枚举名称。 */
    @TableField("source_type")
    private String sourceType;

    /** 来源记录的稳定业务ID。 */
    @TableField("source_id")
    private String sourceId;

    /** 提取时来源内容的小写SHA-256。 */
    @TableField("source_hash")
    private String sourceHash;

    /** 证据ID列表的JSON数组。 */
    @TableField("evidence_refs_json")
    private String evidenceRefsJson;

    /** 当前候选准备替代的旧Memory ID，没有时为空。 */
    @TableField("supersedes_id")
    private String supersedesId;

    /** 乐观锁版本，新建记录从0开始。 */
    @TableField("version")
    private Long version;

    /** 创建时间，统一使用UTC Instant。 */
    @TableField("created_at")
    private Instant createdAt;

    /** 最后状态更新时间，统一使用UTC Instant。 */
    @TableField("updated_at")
    private Instant updatedAt;
}