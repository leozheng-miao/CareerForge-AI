package com.leo.careerforgeai.auth.api;

import com.leo.careerforgeai.auth.config.AuthProperties;
import com.leo.careerforgeai.auth.config.RefreshTokenCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * @program: CareerForge-AI
 * @description: 读取、签发和清理HttpOnly Refresh Token Cookie
 * @author: Miao Zheng
 * @date: 2026-09-08
 **/
@Component
@ConditionalOnProperty(prefix = "careerforge.auth", name = "enabled", havingValue = "true")
final class RefreshTokenCookieManager {
    private final AuthProperties authProperties;
    private final RefreshTokenCookieProperties cookieProperties;

    RefreshTokenCookieManager(AuthProperties authProperties,
                              RefreshTokenCookieProperties cookieProperties) {
        this.authProperties = Objects.requireNonNull(authProperties, "authProperties不能为空");
        this.cookieProperties = Objects.requireNonNull(cookieProperties, "cookieProperties不能为空");
    }

    Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = Objects.requireNonNull(request, "request不能为空").getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie cookie : cookies) {
            if (cookieProperties.name().equals(cookie.getName())
                    && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    void issue(HttpServletResponse response, String refreshToken) {
        write(response, baseCookie(Objects.requireNonNull(refreshToken, "refreshToken不能为空"))
                .maxAge(authProperties.refreshTokenTtl()).build());
    }

    void clear(HttpServletResponse response) {
        write(response, baseCookie("").maxAge(Duration.ZERO).build());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(cookieProperties.name(), value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(cookieProperties.path());
    }

    private void write(HttpServletResponse response, ResponseCookie cookie) {
        Objects.requireNonNull(response, "response不能为空")
                .addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}