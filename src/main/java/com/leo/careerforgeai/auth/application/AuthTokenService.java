package com.leo.careerforgeai.auth.application;

import com.leo.careerforgeai.auth.config.AuthProperties;
import com.leo.careerforgeai.auth.domain.RefreshToken;
import com.leo.careerforgeai.auth.domain.UserAccount;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.HexFormat;

/**
 * @program: CareerForge-AI
 * @description: 签发短期JWT Access Token并生成只持久化Hash的随机Refresh Token
 * @author: Miao Zheng
 * @date: 2026-09-02
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.auth", name = "enabled", havingValue = "true")
public class AuthTokenService {

    private final AuthProperties properties;
    private final JwtEncoder jwtEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthTokenService(AuthProperties properties, JwtEncoder jwtEncoder) {
        this.properties = Objects.requireNonNull(properties, "properties不能为空");
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "jwtEncoder不能为空");
    }

    public IssuedAccessToken issueAccessToken(UserAccount account, Instant issuedAt) {
        Objects.requireNonNull(account, "account不能为空");
        Objects.requireNonNull(issuedAt, "issuedAt不能为空");
        if (!account.active()) throw new IllegalStateException("禁用账户不能签发Access Token");

        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(account.userId().value())
                .audience(List.of(properties.audience()))
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("token_type", "access")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, expiresAt);
    }

    public IssuedRefreshToken issueRefreshToken(
            ActorId userId,
            UUID familyId,
            UUID parentTokenId,
            Instant issuedAt
    ) {
        Objects.requireNonNull(userId, "userId不能为空");
        Objects.requireNonNull(issuedAt, "issuedAt不能为空");
        UUID resolvedFamilyId = familyId == null ? UUID.randomUUID() : familyId;
        byte[] randomBytes = new byte[properties.refreshTokenBytes()];
        secureRandom.nextBytes(randomBytes);
        String rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        RefreshToken token = RefreshToken.issue(
                UUID.randomUUID(),
                userId,
                resolvedFamilyId,
                parentTokenId,
                hashRefreshToken(rawValue),
                issuedAt,
                issuedAt.plus(properties.refreshTokenTtl())
        );
        return new IssuedRefreshToken(rawValue, token);
    }

    public String hashRefreshToken(String rawValue) {
        if (rawValue == null || rawValue.isBlank() || rawValue.length() > 512) {
            throw new IllegalArgumentException("Refresh Token格式不合法");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JVM不支持SHA-256", exception);
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 返回给客户端的短期Access Token
     * @author: Miao Zheng
     * @date: 2026-09-02
     * @param value JWT原始值
     * @param expiresAt 过期时间
     **/
    public record IssuedAccessToken(String value, Instant expiresAt) {
        public IssuedAccessToken {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Access Token不能为空");
            Objects.requireNonNull(expiresAt, "expiresAt不能为空");
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 临时携带原始Refresh Token及其待持久化Hash实体
     * @author: Miao Zheng
     * @date: 2026-09-02
     * @param value 仅返回客户端的随机Token
     * @param persistentToken 只包含Token Hash的持久化对象
     **/
    public record IssuedRefreshToken(String value, RefreshToken persistentToken) {
        public IssuedRefreshToken {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Refresh Token不能为空");
            Objects.requireNonNull(persistentToken, "persistentToken不能为空");
        }
    }
}