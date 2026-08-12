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
 * @description: 映射coaching_session表并保存会话状态、消息序号和乐观锁版本
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("coaching_session")
public class CoachingSessionEntity {

    /** 服务端生成的会话UUID。 */
    @TableId(value = "session_id", type = IdType.INPUT)
    private String sessionId;

    /** 会话所属用户。 */
    @TableField("owner_id")
    private String ownerId;

    /** 用户可见的会话标题。 */
    @TableField("title")
    private String title;

    /** ACTIVE或CLOSED。 */
    @TableField("session_status")
    private String sessionStatus;

    /** 下一条消息应使用的会话内序号。 */
    @TableField("next_turn_sequence")
    private Long nextTurnSequence;

    /** 乐观锁版本。 */
    @TableField("version")
    private Long version;

    /** 会话创建时间。 */
    @TableField("created_at")
    private Instant createdAt;

    /** 最后更新时间。 */
    @TableField("updated_at")
    private Instant updatedAt;

    /** 会话关闭时间，ACTIVE时为空。 */
    @TableField("closed_at")
    private Instant closedAt;
}