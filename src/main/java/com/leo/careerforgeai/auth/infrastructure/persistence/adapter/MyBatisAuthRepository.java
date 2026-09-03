package com.leo.careerforgeai.auth.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.careerforgeai.auth.application.port.AuthRepository;
import com.leo.careerforgeai.auth.domain.RefreshToken;
import com.leo.careerforgeai.auth.domain.UserAccount;
import com.leo.careerforgeai.auth.infrastructure.persistence.entity.RefreshTokenEntity;
import com.leo.careerforgeai.auth.infrastructure.persistence.entity.UserAccountEntity;
import com.leo.careerforgeai.auth.infrastructure.persistence.mapper.RefreshTokenMapper;
import com.leo.careerforgeai.auth.infrastructure.persistence.mapper.UserAccountMapper;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 使用MyBatis实现账户持久化、Token原子轮换和owner隔离撤销
 * @author: Miao Zheng
 * @date: 2026-09-02
 **/
@Repository
@ConditionalOnProperty(
        prefix = "careerforge.persistence",
        name = "enabled",
        havingValue = "true"
)
public class MyBatisAuthRepository implements AuthRepository {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private final UserAccountMapper accountMapper;
    private final RefreshTokenMapper tokenMapper;

    public MyBatisAuthRepository(
            UserAccountMapper accountMapper,
            RefreshTokenMapper tokenMapper
    ) {
        this.accountMapper = Objects.requireNonNull(accountMapper, "accountMapper不能为空");
        this.tokenMapper = Objects.requireNonNull(tokenMapper, "tokenMapper不能为空");
    }

    @Override
    public void createAccount(UserAccount account) {
        Objects.requireNonNull(account, "account不能为空");
        requireSingleRow(accountMapper.insert(toEntity(account)), "账户创建失败");
    }

    @Override
    public Optional<UserAccount> findAccountById(ActorId userId) {
        Objects.requireNonNull(userId, "userId不能为空");
        return Optional.ofNullable(accountMapper.selectById(userId.value())).map(this::toDomain);
    }

    @Override
    public Optional<UserAccount> findAccountByEmail(String normalizedEmail) {
        String email = UserAccount.normalizeEmail(normalizedEmail);
        LambdaQueryWrapper<UserAccountEntity> query = new LambdaQueryWrapper<>();
        query.eq(UserAccountEntity::getEmail, email);
        return Optional.ofNullable(accountMapper.selectOne(query)).map(this::toDomain);
    }

    @Override
    public boolean updateAccountIfVersionMatches(
            UserAccount account,
            long expectedVersion
    ) {
        Objects.requireNonNull(account, "account不能为空");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion不能小于0");
        if (account.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("账户新version必须比expectedVersion增加1");
        }
        return accountMapper.updateIfVersionMatches(toEntity(account), expectedVersion) == 1;
    }

    @Override
    public void createRefreshToken(RefreshToken refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken不能为空");
        if (refreshToken.status() != RefreshToken.Status.ACTIVE || refreshToken.version() != 0) {
            throw new IllegalArgumentException("只能创建初始ACTIVE Refresh Token");
        }
        requireSingleRow(tokenMapper.insert(toEntity(refreshToken)), "Refresh Token创建失败");
    }

    @Override
    public Optional<RefreshToken> findRefreshTokenByHash(String tokenHash) {
        String normalizedHash = requireTokenHash(tokenHash);
        LambdaQueryWrapper<RefreshTokenEntity> query = new LambdaQueryWrapper<>();
        query.eq(RefreshTokenEntity::getTokenHash, normalizedHash);
        return Optional.ofNullable(tokenMapper.selectOne(query)).map(this::toDomain);
    }

    @Override
    @Transactional
    public boolean rotateRefreshToken(
            RefreshToken rotatedToken,
            RefreshToken replacementToken,
            long expectedVersion
    ) {
        validateRotation(rotatedToken, replacementToken, expectedVersion);
        int updated = tokenMapper.rotateIfActive(toEntity(rotatedToken), expectedVersion);
        if (updated != 1) return false;
        requireSingleRow(tokenMapper.insert(toEntity(replacementToken)), "新Refresh Token创建失败");
        return true;
    }

    @Override
    public boolean revokeActiveToken(
            ActorId userId,
            String tokenHash,
            Instant revokedAt
    ) {
        Objects.requireNonNull(userId, "userId不能为空");
        Objects.requireNonNull(revokedAt, "revokedAt不能为空");
        return tokenMapper.revokeActiveToken(
                userId.value(), requireTokenHash(tokenHash), revokedAt) == 1;
    }

    @Override
    public int revokeActiveFamily(
            ActorId userId,
            UUID familyId,
            Instant revokedAt
    ) {
        Objects.requireNonNull(userId, "userId不能为空");
        Objects.requireNonNull(familyId, "familyId不能为空");
        Objects.requireNonNull(revokedAt, "revokedAt不能为空");
        return tokenMapper.revokeActiveFamily(
                userId.value(), familyId.toString(), revokedAt);
    }

    private void validateRotation(
            RefreshToken rotated,
            RefreshToken replacement,
            long expectedVersion
    ) {
        Objects.requireNonNull(rotated, "rotatedToken不能为空");
        Objects.requireNonNull(replacement, "replacementToken不能为空");
        if (expectedVersion < 0 || rotated.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("轮换Token版本不合法");
        }
        if (rotated.status() != RefreshToken.Status.ROTATED
                || replacement.status() != RefreshToken.Status.ACTIVE
                || replacement.version() != 0) {
            throw new IllegalArgumentException("轮换Token状态不合法");
        }
        if (!rotated.userId().equals(replacement.userId())
                || !rotated.familyId().equals(replacement.familyId())
                || !rotated.tokenId().equals(replacement.parentTokenId())) {
            throw new IllegalArgumentException("新旧Refresh Token的用户、家族或父子关系不一致");
        }
    }

    private UserAccountEntity toEntity(UserAccount account) {
        UserAccountEntity entity = new UserAccountEntity();
        entity.setUserId(account.userId().value());
        entity.setEmail(account.email());
        entity.setDisplayName(account.displayName());
        entity.setPasswordHash(account.passwordHash());
        entity.setAccountStatus(account.status().name());
        entity.setVersion(account.version());
        entity.setCreatedAt(account.createdAt());
        entity.setUpdatedAt(account.updatedAt());
        entity.setLastLoginAt(account.lastLoginAt());
        entity.setDisabledAt(account.disabledAt());
        return entity;
    }

    private UserAccount toDomain(UserAccountEntity entity) {
        return new UserAccount(
                new ActorId(entity.getUserId()),
                entity.getEmail(),
                entity.getDisplayName(),
                entity.getPasswordHash(),
                UserAccount.Status.valueOf(entity.getAccountStatus()),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLastLoginAt(),
                entity.getDisabledAt()
        );
    }

    private RefreshTokenEntity toEntity(RefreshToken token) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setRefreshTokenId(token.tokenId().toString());
        entity.setUserId(token.userId().value());
        entity.setFamilyId(token.familyId().toString());
        entity.setParentTokenId(token.parentTokenId() == null ? null : token.parentTokenId().toString());
        entity.setTokenHash(token.tokenHash());
        entity.setTokenStatus(token.status().name());
        entity.setExpiresAt(token.expiresAt());
        entity.setCreatedAt(token.createdAt());
        entity.setRotatedAt(token.rotatedAt());
        entity.setRevokedAt(token.revokedAt());
        entity.setVersion(token.version());
        return entity;
    }

    private RefreshToken toDomain(RefreshTokenEntity entity) {
        return new RefreshToken(
                UUID.fromString(entity.getRefreshTokenId()),
                new ActorId(entity.getUserId()),
                UUID.fromString(entity.getFamilyId()),
                entity.getParentTokenId() == null
                        ? null
                        : UUID.fromString(entity.getParentTokenId()),
                entity.getTokenHash(),
                RefreshToken.Status.valueOf(entity.getTokenStatus()),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getRotatedAt(),
                entity.getRevokedAt(),
                entity.getVersion()
        );
    }

    private static String requireTokenHash(String tokenHash) {
        if (tokenHash == null || !SHA256_PATTERN.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException("tokenHash必须是64位小写SHA-256");
        }
        return tokenHash;
    }

    private static void requireSingleRow(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new IllegalStateException(message + ": affectedRows=" + affectedRows);
        }
    }
}