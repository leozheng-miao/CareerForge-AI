package com.leo.careerforgeai.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 定义Refresh Token持久Cookie的安全属性
 * @author: Miao Zheng
 * @date: 2026-09-08
 * @param name Cookie名称
 * @param path Cookie发送路径
 * @param secure 是否仅允许HTTPS传输
 * @param sameSite SameSite策略
 **/
@ConfigurationProperties(prefix = "careerforge.refresh-token-cookie", ignoreUnknownFields = false)
public record RefreshTokenCookieProperties(
        String name,
        String path,
        boolean secure,
        String sameSite
) {
    private static final Pattern COOKIE_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    public RefreshTokenCookieProperties {
        if (name == null || !COOKIE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Refresh Token Cookie名称不合法");
        }
        if (path == null || path.length() > 128 || !path.startsWith("/")
                || path.chars().anyMatch(value -> value <= 0x20 || value == ';' || value == 0x7f)) {
            throw new IllegalArgumentException("Refresh Token Cookie路径不合法");
        }
        sameSite = switch (sameSite == null ? "" : sameSite.strip().toLowerCase(Locale.ROOT)) {
            case "strict" -> "Strict";
            case "lax" -> "Lax";
            default -> throw new IllegalArgumentException("Refresh Token Cookie SameSite必须是Strict或Lax");
        };
    }
}