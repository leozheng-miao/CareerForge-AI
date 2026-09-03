package com.leo.careerforgeai.auth.application.port;

import com.leo.careerforgeai.auth.domain.RefreshToken;
import com.leo.careerforgeai.auth.domain.UserAccount;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义账户、Refresh Token轮换和家族撤销的MySQL持久化边界
 * @author: Miao Zheng
 * @date: 2026-09-02
 **/
public interface AuthRepository {

    void createAccount(UserAccount account);

    Optional<UserAccount> findAccountById(ActorId userId);

    Optional<UserAccount> findAccountByEmail(String normalizedEmail);

    boolean updateAccountIfVersionMatches(UserAccount account, long expectedVersion);

    void createRefreshToken(RefreshToken refreshToken);

    Optional<RefreshToken> findRefreshTokenByHash(String tokenHash);

    boolean rotateRefreshToken(
            RefreshToken rotatedToken,
            RefreshToken replacementToken,
            long expectedVersion
    );

    boolean revokeActiveToken(
            ActorId userId,
            String tokenHash,
            Instant revokedAt
    );

    int revokeActiveFamily(
            ActorId userId,
            UUID familyId,
            Instant revokedAt
    );
}