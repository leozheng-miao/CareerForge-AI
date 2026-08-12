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
 * @description: 映射coaching_turn表并保存可审计用户消息、助手回答和受控失败记录
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("coaching_turn")
public class ConversationTurnEntity {

    /** 服务端生成的消息UUID。 */
    @TableId(value = "turn_id", type = IdType.INPUT)
    private String turnId;

    /** 消息所属会话。 */
    @TableField("session_id")
    private String sessionId;

    /** 同一轮用户问题和助手回答共享的关联UUID。 */
    @TableField("exchange_id")
    private String exchangeId;

    /** 消息所属用户。 */
    @TableField("owner_id")
    private String ownerId;

    /** 会话内严格递增的消息序号。 */
    @TableField("turn_sequence")
    private Long turnSequence;

    /** USER或ASSISTANT。 */
    @TableField("turn_role")
    private String turnRole;

    /** COMPLETED或FAILED。 */
    @TableField("turn_status")
    private String turnStatus;

    /** 已完成消息正文，FAILED时为空。 */
    @TableField("content")
    private String content;

    /** 消息正文的小写SHA-256，FAILED时为空。 */
    @TableField("content_hash")
    private String contentHash;

    /** 助手消息对应的Agent Run ID。 */
    @TableField("agent_run_id")
    private String agentRunId;

    /** 助手执行失败时的稳定错误分类。 */
    @TableField("failure_code")
    private String failureCode;

    /** 服务端记录时间。 */
    @TableField("created_at")
    private Instant createdAt;
}