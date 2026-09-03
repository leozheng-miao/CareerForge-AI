package com.leo.careerforgeai.auth.application;

import com.leo.careerforgeai.auth.application.AuthTokenService.IssuedAccessToken;
import com.leo.careerforgeai.auth.application.AuthTokenService.IssuedRefreshToken;
import com.leo.careerforgeai.auth.application.port.AuthRepository;
import com.leo.careerforgeai.auth.domain.RefreshToken;
import com.leo.careerforgeai.auth.domain.UserAccount;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description: 验证认证应用服务的注册登录、Token轮换重放、退出及当前用户边界
 * @author: Miao Zheng
 * @date: 2026-09-03
 **/
@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final ActorId USER_ID = new ActorId("auth-user-001");
    private static final UUID TOKEN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REPLACEMENT_TOKEN_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID FAMILY_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String PASSWORD_HASH = "{bcrypt}encoded-password";
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Mock private AuthRepository repository;
    @Mock private AuthTokenService tokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CurrentActorProvider currentActorProvider;

    private AuthApplicationService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(PASSWORD_HASH);
        service = new AuthApplicationService(repository, tokenService, passwordEncoder,
                currentActorProvider, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证注册规范化邮箱、仅持久化密码Hash并创建首个Refresh Token
     * @author: Miao Zheng
     * @date: 2026-09-03
     **/
    @Test
    void shouldRegisterAccountAndIssueInitialSession() {
        when(repository.findAccountByEmail("learner@example.com")).thenReturn(Optional.empty());
        stubInitialSession();

        AuthApplicationService.AuthResult result =
                service.register(" Learner@Example.com ", " Learner ", "safe-password");

        ArgumentCaptor<UserAccount> accountCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).createAccount(accountCaptor.capture());
        UserAccount saved = accountCaptor.getValue();
        assertThat(saved.userId()).isEqualTo(result.userId());
        assertThat(saved.email()).isEqualTo("learner@example.com");
        assertThat(saved.displayName()).isEqualTo("Learner");
        assertThat(saved.passwordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(saved.passwordHash()).doesNotContain("safe-password");
        verify(repository).createRefreshToken(argThat(token -> token.userId().equals(result.userId())));
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证登录成功后通过乐观锁记录登录时间并签发新会话
     * @author: Miao Zheng
     * @date: 2026-09-03
     **/
    @Test
    void shouldRecordSuccessfulLoginAndIssueSession() {
        UserAccount account = account();
        when(repository.findAccountByEmail(account.email())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("correct-password", PASSWORD_HASH)).thenReturn(true);
        when(repository.updateAccountIfVersionMatches(any(UserAccount.class), eq(0L))).thenReturn(true);
        stubInitialSession();

        AuthApplicationService.AuthResult result =
                service.login("USER@EXAMPLE.COM", "correct-password");

        verify(repository).updateAccountIfVersionMatches(argThat(updated ->
                updated.version() == 1 && NOW.equals(updated.lastLoginAt())), eq(0L));
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证未知邮箱仍执行虚拟密码比较并返回统一凭证错误
     * @author: Miao Zheng
     * @date: 2026-09-03
     **/
    @Test
    void shouldRejectUnknownAccountWithGenericCredentialsFailure() {
        when(repository.findAccountByEmail("missing@example.com")).thenReturn(Optional.empty());

        AuthException exception = catchThrowableOfType(
                () -> service.login("missing@example.com", "wrong-password"),
                AuthException.class
        );

        assertThat(exception.reason()).isEqualTo(AuthException.Reason.INVALID_CREDENTIALS);
        verify(passwordEncoder).matches("wrong-password", PASSWORD_HASH);
        verifyNoInteractions(tokenService);
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证Refresh Token只能原子轮换并保留家族和父子关系
     * @author: Miao Zheng
     * @date: 2026-09-03
     **/
    @Test
    void shouldRotateRefreshTokenAndIssueNewAccessToken() {
        UserAccount account = account();
        RefreshToken current = activeToken(TOKEN_ID, null, HASH_A);
        RefreshToken replacement = RefreshToken.issue(
                REPLACEMENT_TOKEN_ID, USER_ID, FAMILY_ID, TOKEN_ID, HASH_B,
                NOW, NOW.plusSeconds(2_592_000)
        );
        when(tokenService.hashRefreshToken("current-refresh")).thenReturn(HASH_A);
        when(repository.findRefreshTokenByHash(HASH_A)).thenReturn(Optional.of(current));
        when(repository.findAccountById(USER_ID)).thenReturn(Optional.of(account));
        when(tokenService.issueRefreshToken(USER_ID, FAMILY_ID, TOKEN_ID, NOW))
                .thenReturn(new IssuedRefreshToken("replacement-refresh", replacement));
        when(repository.rotateRefreshToken(current.rotate(NOW), replacement, 0)).thenReturn(true);
        when(tokenService.issueAccessToken(account, NOW))
                .thenReturn(new IssuedAccessToken("replacement-access", NOW.plusSeconds(900)));

        AuthApplicationService.AuthResult result = service.refresh("current-refresh");

        assertThat(result.accessToken()).isEqualTo("replacement-access");
        assertThat(result.refreshToken()).isEqualTo("replacement-refresh");
        verify(repository).rotateRefreshToken(current.rotate(NOW), replacement, 0);
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证已轮换Token再次使用时撤销整个Token家族
     * @author: Miao Zheng
     * @date: 2026-09-03
     **/
    @Test
    void shouldRevokeTokenFamilyWhenRotatedTokenIsReplayed() {
        RefreshToken replayed = activeToken(TOKEN_ID, null, HASH_A).rotate(NOW.minusSeconds(1));
        when(tokenService.hashRefreshToken("replayed-refresh")).thenReturn(HASH_A);
        when(repository.findRefreshTokenByHash(HASH_A)).thenReturn(Optional.of(replayed));

        AuthException exception = catchThrowableOfType(
                () -> service.refresh("replayed-refresh"),
                AuthException.class
        );

        assertThat(exception.reason()).isEqualTo(AuthException.Reason.REFRESH_TOKEN_REPLAYED);
        verify(repository).revokeActiveFamily(USER_ID, FAMILY_ID, NOW);
        verify(tokenService, never()).issueAccessToken(any(), any());
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证退出和GET me始终使用认证上下文中的用户而非客户端ownerId
     * @author: Miao Zheng
     * @date: 2026-09-03
     **/
    @Test
    void shouldUseCurrentActorForLogoutAndCurrentUserQuery() {
        when(currentActorProvider.currentActor()).thenReturn(USER_ID);
        when(tokenService.hashRefreshToken("owned-refresh")).thenReturn(HASH_A);
        when(repository.findAccountById(USER_ID)).thenReturn(Optional.of(account()));

        service.logout("owned-refresh");
        AuthApplicationService.CurrentUser currentUser = service.me();

        verify(repository).revokeActiveToken(USER_ID, HASH_A, NOW);
        verify(repository).findAccountById(USER_ID);
        assertThat(currentUser.userId()).isEqualTo(USER_ID);
        assertThat(currentUser.email()).isEqualTo("user@example.com");
    }

    private void stubInitialSession() {
        when(tokenService.issueAccessToken(any(UserAccount.class), eq(NOW)))
                .thenReturn(new IssuedAccessToken("access-token", NOW.plusSeconds(900)));
        when(tokenService.issueRefreshToken(any(ActorId.class), isNull(), isNull(), eq(NOW)))
                .thenAnswer(invocation -> new IssuedRefreshToken(
                        "refresh-token",
                        RefreshToken.issue(TOKEN_ID, invocation.getArgument(0), FAMILY_ID, null,
                                HASH_A, NOW, NOW.plusSeconds(2_592_000))
                ));
    }

    private UserAccount account() {
        return UserAccount.register(USER_ID, "user@example.com", "User",
                PASSWORD_HASH, NOW.minusSeconds(3600));
    }

    private RefreshToken activeToken(UUID tokenId, UUID parentTokenId, String hash) {
        return RefreshToken.issue(tokenId, USER_ID, FAMILY_ID, parentTokenId, hash,
                NOW.minusSeconds(3600), NOW.plusSeconds(2_592_000));
    }
}