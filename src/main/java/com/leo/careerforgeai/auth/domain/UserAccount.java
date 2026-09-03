package com.leo.careerforgeai.auth.domain;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 表示可认证用户账户及其启用状态和登录版本
 * @author: Miao Zheng
 * @date: 2026-09-02
 * @param userId 用户唯一身份，同时作为业务数据ownerId
 * @param email 规范化小写邮箱
 * @param displayName 用户展示名称
 * @param passwordHash 自适应密码编码器生成的Hash
 * @param status 账户状态
 * @param version 乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param lastLoginAt 最近登录时间
 * @param disabledAt 禁用时间
 **/
public record UserAccount(
        ActorId userId,
        String email,
        String displayName,
        String passwordHash,
        Status status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt,
        Instant disabledAt
) {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public UserAccount {
        Objects.requireNonNull(userId, "userId不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt不能为空");
        email = normalizeEmail(email);
        displayName = requireText(displayName, "displayName", 80);
        passwordHash = requireText(passwordHash, "passwordHash", 255);
        if (version < 0) throw new IllegalArgumentException("version不能小于0");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt不能早于createdAt");
        if (lastLoginAt != null && lastLoginAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("lastLoginAt不能早于createdAt");
        }
        if (status == Status.ACTIVE && disabledAt != null) {
            throw new IllegalArgumentException("ACTIVE账户不能包含disabledAt");
        }
        if (status == Status.DISABLED && (disabledAt == null || disabledAt.isBefore(createdAt))) {
            throw new IllegalArgumentException("DISABLED账户必须包含合法disabledAt");
        }
    }

    public static UserAccount register(
            ActorId userId,
            String email,
            String displayName,
            String passwordHash,
            Instant now
    ) {
        Objects.requireNonNull(now, "now不能为空");
        return new UserAccount(
                userId, email, displayName, passwordHash,
                Status.ACTIVE, 0, now, now, null, null);
    }

    public UserAccount recordLogin(Instant now) {
        requireTransitionTime(now);
        if (status != Status.ACTIVE) throw new IllegalStateException("禁用账户不能登录");
        return new UserAccount(
                userId, email, displayName, passwordHash, status,
                version + 1, createdAt, now, now, null);
    }

    public UserAccount disable(Instant now) {
        requireTransitionTime(now);
        if (status != Status.ACTIVE) throw new IllegalStateException("只有ACTIVE账户可以禁用");
        return new UserAccount(
                userId, email, displayName, passwordHash,
                Status.DISABLED, version + 1, createdAt, now, lastLoginAt, now);
    }

    public boolean active() {
        return status == Status.ACTIVE;
    }

    public static String normalizeEmail(String email) {
        if (email == null) throw new IllegalArgumentException("email不能为空");
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() < 3 || normalized.length() > 254
                || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("email格式不合法");
        }
        return normalized;
    }

    private void requireTransitionTime(Instant now) {
        Objects.requireNonNull(now, "now不能为空");
        if (now.isBefore(updatedAt)) throw new IllegalArgumentException("状态变更时间不能早于updatedAt");
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        String normalized = value.strip();
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength);
        }
        return normalized;
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义账户是否允许认证
     * @author: Miao Zheng
     * @date: 2026-09-02
     **/
    public enum Status {
        ACTIVE,
        DISABLED
    }
}