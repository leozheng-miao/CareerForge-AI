package com.leo.careerforgeai.auth.application;

import com.leo.careerforgeai.auth.application.AuthTokenService.IssuedAccessToken;
import com.leo.careerforgeai.auth.application.AuthTokenService.IssuedRefreshToken;
import com.leo.careerforgeai.auth.application.port.AuthRepository;
import com.leo.careerforgeai.auth.domain.RefreshToken;
import com.leo.careerforgeai.auth.domain.UserAccount;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 编排注册、登录、Token轮换、重放家族撤销、退出和当前账户查询
 * @author: Miao Zheng
 * @date: 2026-09-02
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.auth", name = "enabled", havingValue = "true")
public class AuthApplicationService {

    private final AuthRepository repository;
    private final AuthTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final CurrentActorProvider currentActorProvider;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthApplicationService(
            AuthRepository repository,
            AuthTokenService tokenService,
            PasswordEncoder passwordEncoder,
            CurrentActorProvider currentActorProvider,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService不能为空");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder不能为空");
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResult register(
            String email,
            String displayName,
            String rawPassword
    ) {
        String normalizedEmail = UserAccount.normalizeEmail(email);
        requirePassword(rawPassword);
        if (repository.findAccountByEmail(normalizedEmail).isPresent()) {
            throw new AuthException(AuthException.Reason.EMAIL_ALREADY_EXISTS);
        }

        Instant now = clock.instant();
        UserAccount account = UserAccount.register(
                new ActorId(UUID.randomUUID().toString()),
                normalizedEmail,
                displayName,
                passwordEncoder.encode(rawPassword),
                now
        );
        try {
            repository.createAccount(account);
        } catch (DataIntegrityViolationException exception) {
            throw new AuthException(AuthException.Reason.EMAIL_ALREADY_EXISTS);
        }
        return issueInitialSession(account, now);
    }

    @Transactional
    public AuthResult login(String email, String rawPassword) {
        String normalizedEmail;
        try {
            normalizedEmail = UserAccount.normalizeEmail(email);
        } catch (IllegalArgumentException exception) {
            passwordEncoder.matches(requirePasswordForComparison(rawPassword), dummyPasswordHash);
            throw new AuthException(AuthException.Reason.INVALID_CREDENTIALS);
        }

        UserAccount account = repository.findAccountByEmail(normalizedEmail).orElse(null);
        String encodedPassword = account == null ? dummyPasswordHash : account.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(
                requirePasswordForComparison(rawPassword),
                encodedPassword
        );
        if (account == null || !passwordMatches) {
            throw new AuthException(AuthException.Reason.INVALID_CREDENTIALS);
        }
        requireActive(account);

        Instant now = clock.instant();
        UserAccount loggedIn = recordSuccessfulLogin(account, now);
        return issueInitialSession(loggedIn, now);
    }

    public AuthResult refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        String tokenHash = hashRefreshTokenOrThrow(rawRefreshToken);
        RefreshToken current = repository.findRefreshTokenByHash(tokenHash)
                .orElseThrow(() -> new AuthException(AuthException.Reason.REFRESH_TOKEN_INVALID));

        if (current.status() == RefreshToken.Status.ROTATED) {
            repository.revokeActiveFamily(current.userId(), current.familyId(), now);
            throw new AuthException(AuthException.Reason.REFRESH_TOKEN_REPLAYED);
        }
        if (current.status() != RefreshToken.Status.ACTIVE) {
            throw new AuthException(AuthException.Reason.REFRESH_TOKEN_INVALID);
        }
        if (current.expiredAt(now)) {
            repository.revokeActiveToken(current.userId(), current.tokenHash(), now);
            throw new AuthException(AuthException.Reason.REFRESH_TOKEN_INVALID);
        }

        UserAccount account = repository.findAccountById(current.userId())
                .orElseThrow(() -> new AuthException(AuthException.Reason.REFRESH_TOKEN_INVALID));
        if (!account.active()) {
            repository.revokeActiveFamily(current.userId(), current.familyId(), now);
            throw new AuthException(AuthException.Reason.ACCOUNT_DISABLED);
        }

        IssuedRefreshToken replacement = tokenService.issueRefreshToken(
                current.userId(),
                current.familyId(),
                current.tokenId(),
                now
        );
        boolean rotated;
        try {
            rotated = repository.rotateRefreshToken(
                    current.rotate(now),
                    replacement.persistentToken(),
                    current.version()
            );
        } catch (DataIntegrityViolationException exception) {
            rotated = false;
        }
        if (!rotated) {
            repository.revokeActiveFamily(current.userId(), current.familyId(), now);
            throw new AuthException(AuthException.Reason.REFRESH_TOKEN_REPLAYED);
        }

        IssuedAccessToken accessToken = tokenService.issueAccessToken(account, now);
        return toResult(account, accessToken, replacement);
    }

    public void logout(String rawRefreshToken) {
        ActorId currentActor = currentActorProvider.currentActor();
        String tokenHash;
        try {
            tokenHash = tokenService.hashRefreshToken(rawRefreshToken);
        } catch (IllegalArgumentException exception) {
            return;
        }
        repository.revokeActiveToken(currentActor, tokenHash, clock.instant());
    }

    public CurrentUser me() {
        ActorId currentActor = currentActorProvider.currentActor();
        UserAccount account = repository.findAccountById(currentActor)
                .orElseThrow(() -> new AuthException(AuthException.Reason.ACCOUNT_NOT_FOUND));
        requireActive(account);
        return new CurrentUser(
                account.userId(),
                account.email(),
                account.displayName(),
                account.createdAt(),
                account.lastLoginAt()
        );
    }

    private AuthResult issueInitialSession(UserAccount account, Instant now) {
        IssuedAccessToken accessToken = tokenService.issueAccessToken(account, now);
        IssuedRefreshToken refreshToken = tokenService.issueRefreshToken(
                account.userId(), null, null, now);
        repository.createRefreshToken(refreshToken.persistentToken());
        return toResult(account, accessToken, refreshToken);
    }

    private UserAccount recordSuccessfulLogin(UserAccount current, Instant now) {
        UserAccount updated = current.recordLogin(now);
        if (repository.updateAccountIfVersionMatches(updated, current.version())) return updated;

        UserAccount reloaded = repository.findAccountById(current.userId())
                .orElseThrow(() -> new AuthException(AuthException.Reason.AUTH_STATE_CONFLICT));
        requireActive(reloaded);
        UserAccount retried = reloaded.recordLogin(now);
        if (!repository.updateAccountIfVersionMatches(retried, reloaded.version())) {
            throw new AuthException(AuthException.Reason.AUTH_STATE_CONFLICT);
        }
        return retried;
    }

    private AuthResult toResult(
            UserAccount account,
            IssuedAccessToken accessToken,
            IssuedRefreshToken refreshToken
    ) {
        return new AuthResult(
                account.userId(),
                account.email(),
                account.displayName(),
                "Bearer",
                accessToken.value(),
                accessToken.expiresAt(),
                refreshToken.value(),
                refreshToken.persistentToken().expiresAt()
        );
    }

    private void requireActive(UserAccount account) {
        if (!account.active()) throw new AuthException(AuthException.Reason.ACCOUNT_DISABLED);
    }

    private String hashRefreshTokenOrThrow(String rawRefreshToken) {
        try {
            return tokenService.hashRefreshToken(rawRefreshToken);
        } catch (IllegalArgumentException exception) {
            throw new AuthException(AuthException.Reason.REFRESH_TOKEN_INVALID);
        }
    }

    private static void requirePassword(String rawPassword) {
        if (rawPassword == null
                || rawPassword.getBytes(StandardCharsets.UTF_8).length < 8
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || rawPassword.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("密码必须为8至72个UTF-8字节且不能包含控制字符");
        }
    }

    private static String requirePasswordForComparison(String rawPassword) {
        if (rawPassword == null
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || rawPassword.chars().anyMatch(Character::isISOControl)) {
            return "";
        }
        return rawPassword;
    }
    /**
     * @program: CareerForge-AI
     * @description: 返回注册、登录或刷新后的安全会话结果
     * @author: Miao Zheng
     * @date: 2026-09-02
     * @param userId 当前用户ID
     * @param email 当前用户邮箱
     * @param displayName 当前展示名称
     * @param tokenType Access Token类型
     * @param accessToken Access Token原始值
     * @param accessTokenExpiresAt Access Token过期时间
     * @param refreshToken Refresh Token原始值
     * @param refreshTokenExpiresAt Refresh Token过期时间
     **/
    public record AuthResult(
            ActorId userId,
            String email,
            String displayName,
            String tokenType,
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt
    ) {
        public AuthResult {
            Objects.requireNonNull(userId, "userId不能为空");
            Objects.requireNonNull(accessTokenExpiresAt, "accessTokenExpiresAt不能为空");
            Objects.requireNonNull(refreshTokenExpiresAt, "refreshTokenExpiresAt不能为空");
            email = requireText(email, "email");
            displayName = requireText(displayName, "displayName");
            tokenType = requireText(tokenType, "tokenType");
            accessToken = requireText(accessToken, "accessToken");
            refreshToken = requireText(refreshToken, "refreshToken");
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 返回GET me所需的非敏感账户信息
     * @author: Miao Zheng
     * @date: 2026-09-02
     * @param userId 当前用户ID
     * @param email 当前用户邮箱
     * @param displayName 当前展示名称
     * @param createdAt 注册时间
     * @param lastLoginAt 最近登录时间
     **/
    public record CurrentUser(
            ActorId userId,
            String email,
            String displayName,
            Instant createdAt,
            Instant lastLoginAt
    ) {
        public CurrentUser {
            Objects.requireNonNull(userId, "userId不能为空");
            Objects.requireNonNull(createdAt, "createdAt不能为空");
            email = requireText(email, "email");
            displayName = requireText(displayName, "displayName");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        return value.strip();
    }
}