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
 * @description: 映射user_account账户身份、密码Hash和生命周期
 * @author: Miao Zheng
 * @date: 2026-09-02
 **/
@Getter
@Setter
@NoArgsConstructor
@TableName("user_account")
public class UserAccountEntity {

    @TableId(value = "user_id", type = IdType.INPUT)
    private String userId;

    @TableField("email")
    private String email;

    @TableField("display_name")
    private String displayName;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("account_status")
    private String accountStatus;

    @TableField("version")
    private Long version;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableField("last_login_at")
    private Instant lastLoginAt;

    @TableField("disabled_at")
    private Instant disabledAt;
}