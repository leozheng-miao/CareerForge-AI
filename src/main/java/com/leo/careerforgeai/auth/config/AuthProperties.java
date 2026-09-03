package com.leo.careerforgeai.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Base64;

/**
 * @program: CareerForge-AI
 * @description: 定义认证开关、JWT身份和Token生命周期
 * @author: Miao Zheng
 * @date: 2026-09-02
 * @param enabled 是否启用正式认证
 * @param issuer Access Token签发者
 * @param audience Access Token受众
 * @param accessTokenSecretBase64 HS256密钥的Base64编码
 * @param accessTokenTtl Access Token有效期
 * @param refreshTokenTtl Refresh Token有效期
 * @param refreshTokenBytes Refresh Token随机字节数
 **/
@ConfigurationProperties(prefix = "careerforge.auth", ignoreUnknownFields = false)
public record AuthProperties(
        boolean enabled,
        String issuer,
        String audience,
        String accessTokenSecretBase64,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        int refreshTokenBytes
) {

    public AuthProperties {
        issuer = requireText(issuer, "issuer");
        audience = requireText(audience, "audience");
        if (accessTokenTtl == null || accessTokenTtl.compareTo(Duration.ofMinutes(1)) < 0
                || accessTokenTtl.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("accessTokenTtl必须在1至30分钟之间");
        }
        if (refreshTokenTtl == null || refreshTokenTtl.compareTo(Duration.ofDays(1)) < 0
                || refreshTokenTtl.compareTo(Duration.ofDays(90)) > 0) {
            throw new IllegalArgumentException("refreshTokenTtl必须在1至90天之间");
        }
        if (refreshTokenBytes < 32 || refreshTokenBytes > 64) {
            throw new IllegalArgumentException("refreshTokenBytes必须在32至64之间");
        }
        if (enabled) decodeSecret(accessTokenSecretBase64);
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(decodeSecret(accessTokenSecretBase64), "HmacSHA256");
    }

    private static byte[] decodeSecret(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("启用认证时必须配置CAREERFORGE_ACCESS_TOKEN_SECRET");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length < 32) throw new IllegalArgumentException("Access Token密钥不能少于256位");
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("CAREERFORGE_ACCESS_TOKEN_SECRET必须是至少32字节的Base64", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        return value.strip();
    }
}