package com.leo.careerforgeai.auth.application;

import com.leo.careerforgeai.auth.config.AuthProperties;
import com.leo.careerforgeai.auth.domain.UserAccount;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Access Token签名声明及Refresh Token随机值和Hash持久化边界
 * @author: Miao Zheng
 * @date: 2026-09-03
 **/
class AuthTokenServiceTest {

    private static final ActorId USER_ID = new ActorId("auth-user-001");
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private AuthProperties properties;
    private AuthTokenService service;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
        );
        properties = new AuthProperties(true, "careerforge-test", "careerforge-api",
                secret, Duration.ofMinutes(15), Duration.ofDays(30), 32);
        service = new AuthTokenService(
                properties,
                NimbusJwtEncoder.withSecretKey(properties.secretKey())
                        .algorithm(MacAlgorithm.HS256).build()
        );
        decoder = NimbusJwtDecoder.withSecretKey(properties.secretKey())
                .macAlgorithm(MacAlgorithm.HS256).build();
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证JWT包含受控身份声明且修改签名后不能通过密码学验证
     * @author: Miao Zheng
     * @date: 2026-09-03
     **/
    @Test
    void shouldIssueSignedAccessTokenWithControlledClaims() {
        UserAccount account = UserAccount.register(
                USER_ID, "user@example.com", "User", "{bcrypt}hash", NOW.minusSeconds(1)
        );

        AuthTokenService.IssuedAccessToken issued = service.issueAccessToken(account, NOW);
        Jwt jwt = decoder.decode(issued.value());

        assertThat(jwt.getSubject()).isEqualTo(USER_ID.value());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(properties.issuer());
        assertThat(jwt.getAudience()).containsExactly(properties.audience());
        assertThat(jwt.getClaimAsString("token_type")).isEqualTo("access");
        assertThat(jwt.getExpiresAt()).isEqualTo(issued.expiresAt());

        String[] parts = issued.value().split("\\.");
        char changed = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + changed + parts[2].substring(1);
        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证Refresh Token原始值不可预测且持久化对象只保存SHA-256
     * @author: Miao Zheng
     * @date: 2026-09-03
     **/
    @Test
    void shouldPersistOnlyRefreshTokenHashAndPreserveRotationLineage() {
        AuthTokenService.IssuedRefreshToken first =
                service.issueRefreshToken(USER_ID, null, null, NOW);
        AuthTokenService.IssuedRefreshToken second = service.issueRefreshToken(
                USER_ID,
                first.persistentToken().familyId(),
                first.persistentToken().tokenId(),
                NOW.plusSeconds(1)
        );

        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(first.persistentToken().tokenHash())
                .isEqualTo(service.hashRefreshToken(first.value()))
                .doesNotContain(first.value());
        assertThat(first.persistentToken().tokenHash()).hasSize(64);
        assertThat(second.persistentToken().familyId())
                .isEqualTo(first.persistentToken().familyId());
        assertThat(second.persistentToken().parentTokenId())
                .isEqualTo(first.persistentToken().tokenId());
    }
}