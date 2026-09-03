package com.leo.careerforgeai.auth.infrastructure.persistence.entity;

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
 * @description: 映射仅保存Hash的Refresh Token及其轮换家族
 * @author: Miao Zheng
 * @date: 2026-09-02
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("auth_refresh_token")
public class RefreshTokenEntity {

    @TableId(value = "refresh_token_id", type = IdType.INPUT)
    private String refreshTokenId;

    @TableField("user_id")
    private String userId;

    @TableField("family_id")
    private String familyId;

    @TableField("parent_token_id")
    private String parentTokenId;

    @TableField("token_hash")
    private String tokenHash;

    @TableField("token_status")
    private String tokenStatus;

    @TableField("expires_at")
    private Instant expiresAt;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("rotated_at")
    private Instant rotatedAt;

    @TableField("revoked_at")
    private Instant revokedAt;

    @TableField("version")
    private Long version;
}