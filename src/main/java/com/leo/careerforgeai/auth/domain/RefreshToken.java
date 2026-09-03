package com.leo.careerforgeai.auth.domain;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 表示只保存Hash的Refresh Token生命周期和轮换家族
 * @author: Miao Zheng
 * @date: 2026-09-02
 * @param tokenId Refresh Token记录ID
 * @param userId Token所属用户
 * @param familyId Token轮换家族ID
 * @param parentTokenId 上一代Token ID
 * @param tokenHash 原始Token的小写SHA-256
 * @param status Token状态
 * @param expiresAt 过期时间
 * @param createdAt 创建时间
 * @param rotatedAt 轮换时间
 * @param revokedAt 撤销时间
 * @param version 乐观锁版本
 **/
public record RefreshToken(
        UUID tokenId,
        ActorId userId,
        UUID familyId,
        UUID parentTokenId,
        String tokenHash,
        Status status,
        Instant expiresAt,
        Instant createdAt,
        Instant rotatedAt,
        Instant revokedAt,
        long version
) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public RefreshToken {
        Objects.requireNonNull(tokenId, "tokenId不能为空");
        Objects.requireNonNull(userId, "userId不能为空");
        Objects.requireNonNull(familyId, "familyId不能为空");
        Objects.requireNonNull(status, "status不能为空");
        Objects.requireNonNull(expiresAt, "expiresAt不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");
        if (parentTokenId != null && parentTokenId.equals(tokenId)) {
            throw new IllegalArgumentException("parentTokenId不能等于tokenId");
        }
        if (tokenHash == null || !SHA256_PATTERN.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException("tokenHash必须是64位小写SHA-256");
        }
        if (!expiresAt.isAfter(createdAt)) throw new IllegalArgumentException("expiresAt必须晚于createdAt");
        if (version < 0) throw new IllegalArgumentException("version不能小于0");
        validateLifecycle(status, createdAt, rotatedAt, revokedAt);
    }

    public static RefreshToken issue(
            UUID tokenId,
            ActorId userId,
            UUID familyId,
            UUID parentTokenId,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new RefreshToken(
                tokenId, userId, familyId, parentTokenId, tokenHash,
                Status.ACTIVE, expiresAt, createdAt, null, null, 0);
    }

    public RefreshToken rotate(Instant now) {
        requireActiveTransition(now);
        return new RefreshToken(
                tokenId, userId, familyId, parentTokenId, tokenHash,
                Status.ROTATED, expiresAt, createdAt, now, null, version + 1);
    }

    public RefreshToken revoke(Instant now) {
        requireActiveTransition(now);
        return new RefreshToken(
                tokenId, userId, familyId, parentTokenId, tokenHash,
                Status.REVOKED, expiresAt, createdAt, null, now, version + 1);
    }

    public boolean expiredAt(Instant now) {
        Objects.requireNonNull(now, "now不能为空");
        return !now.isBefore(expiresAt);
    }

    private void requireActiveTransition(Instant now) {
        Objects.requireNonNull(now, "now不能为空");
        if (status != Status.ACTIVE) throw new IllegalStateException("只有ACTIVE Refresh Token可以变更");
        if (now.isBefore(createdAt)) throw new IllegalArgumentException("状态变更时间不能早于createdAt");
    }

    private static void validateLifecycle(
            Status status,
            Instant createdAt,
            Instant rotatedAt,
            Instant revokedAt
    ) {
        switch (status) {
            case ACTIVE -> {
                if (rotatedAt != null || revokedAt != null) {
                    throw new IllegalArgumentException("ACTIVE Token不能包含轮换或撤销时间");
                }
            }
            case ROTATED -> {
                if (rotatedAt == null || rotatedAt.isBefore(createdAt) || revokedAt != null) {
                    throw new IllegalArgumentException("ROTATED Token生命周期不合法");
                }
            }
            case REVOKED -> {
                if (revokedAt == null || revokedAt.isBefore(createdAt) || rotatedAt != null) {
                    throw new IllegalArgumentException("REVOKED Token生命周期不合法");
                }
            }
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 定义Refresh Token当前是否仍可用于刷新
     * @author: Miao Zheng
     * @date: 2026-09-02
     **/
    public enum Status {
        ACTIVE,
        ROTATED,
        REVOKED
    }
}