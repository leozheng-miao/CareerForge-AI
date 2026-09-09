package com.leo.careerforgeai.auth.api;

import com.leo.careerforgeai.auth.application.AuthApplicationService;
import com.leo.careerforgeai.auth.application.port.AuthLoginRateLimiter;
import com.leo.careerforgeai.auth.config.AuthProperties;
import com.leo.careerforgeai.auth.config.SecurityConfiguration;
import com.leo.careerforgeai.auth.security.SecurityCurrentActorProvider;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.leo.careerforgeai.auth.application.AuthException;
import com.leo.careerforgeai.auth.config.RefreshTokenCookieProperties;
import jakarta.servlet.http.Cookie;

/**
 * @program: CareerForge-AI
 * @description: 验证认证REST入口公开范围、Bearer JWT过滤和URL Token拒绝边界
 * @author: Miao Zheng
 * @date: 2026-09-03
 **/
class AuthSecurityBoundaryTest {

    private static final ActorId USER_ID = new ActorId("auth-user-001");

    private AnnotationConfigWebApplicationContext context;
    private AuthApplicationService service;
    private MockMvc mockMvc;
    private AuthLoginRateLimiter loginRateLimiter;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertyValues.of("careerforge.auth.enabled=true").applyTo(context);
        context.register(TestConfiguration.class);
        context.refresh();
        service = context.getBean(AuthApplicationService.class);
        loginRateLimiter = context.getBean(AuthLoginRateLimiter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void closeContext() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证公开注册签发HttpOnly Cookie且响应不暴露Refresh Token
     * @author: Miao Zheng
     * @date: 2026-09-08
     **/
    @Test
    void shouldAllowPublicRegistrationAndIssueRefreshCookie() throws Exception {
        when(service.register("user@example.com", "User", "safe-password"))
                .thenReturn(authResult("refresh-token"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "email":"user@example.com",
                              "displayName":"User",
                              "password":"safe-password"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(USER_ID.value()))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(cookie().value("careerforge_refresh", "refresh-token"))
                .andExpect(cookie().httpOnly("careerforge_refresh", true))
                .andExpect(cookie().secure("careerforge_refresh", false))
                .andExpect(cookie().path("careerforge_refresh", "/api/auth"))
                .andExpect(cookie().sameSite("careerforge_refresh", "Strict"))
                .andExpect(cookie().maxAge("careerforge_refresh", 2_592_000))
                .andExpect(header().string(
                        "Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")
                ));
    }
    /**
     * @program: CareerForge-AI
     * @description: 验证GET me缺少Bearer Token时返回稳定未登录错误
     * @author: Miao Zheng
     * @date: 2026-09-03
     **/
    @Test
    void shouldRejectProtectedApiWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("未登录"));

        verifyNoInteractions(service);
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证URL参数Token不可信而合法Authorization Header可建立JWT身份
     * @author: Miao Zheng
     * @date: 2026-09-03
     **/
    @Test
    void shouldRejectUrlTokenAndAcceptBearerHeader() throws Exception {
        String accessToken = validAccessToken();
        mockMvc.perform(get("/api/me").queryParam("access_token", accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        when(service.me()).thenAnswer(invocation -> {
            assertThat(new SecurityCurrentActorProvider().currentActor()).isEqualTo(USER_ID);
            return currentUser();
        });
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(USER_ID.value()))
                .andExpect(jsonPath("$.data.email").value("user@example.com"));
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证登录限流拒绝时返回稳定429且不执行密码认证
     * @author: Miao Zheng
     * @date: 2026-09-03
     **/
    @Test
    void shouldRejectRateLimitedLoginBeforePasswordAuthentication() throws Exception {
        when(loginRateLimiter.tryAcquire("203.0.113.10", "user@example.com"))
                .thenReturn(false);

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "email":"user@example.com",
                              "password":"wrong-password"
                            }
                            """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42900))
                .andExpect(jsonPath("$.message")
                        .value("登录尝试过于频繁，请稍后重试"));

        verifyNoInteractions(service);
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证刷新接口从Cookie读取Refresh Token并轮换Cookie
     * @author: Miao Zheng
     * @date: 2026-09-08
     **/
    @Test
    void shouldRefreshSessionFromCookieAndRotateCookie() throws Exception {
        when(service.refresh("current-refresh"))
                .thenReturn(authResult("replacement-refresh"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("careerforge_refresh", "current-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(cookie().value("careerforge_refresh", "replacement-refresh"))
                .andExpect(cookie().httpOnly("careerforge_refresh", true))
                .andExpect(cookie().maxAge("careerforge_refresh", 2_592_000));

        verify(service).refresh("current-refresh");
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证缺少Refresh Token Cookie时返回稳定未授权错误
     * @author: Miao Zheng
     * @date: 2026-09-08
     **/
    @Test
    void shouldRejectRefreshWithoutCookie() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40111))
                .andExpect(jsonPath("$.message").value("Refresh Token无效或已过期"));

        verifyNoInteractions(service);
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证Refresh Token失效时立即清除浏览器Cookie
     * @author: Miao Zheng
     * @date: 2026-09-08
     **/
    @Test
    void shouldClearCookieWhenRefreshTokenIsInvalid() throws Exception {
        when(service.refresh("invalid-refresh"))
                .thenThrow(new AuthException(AuthException.Reason.REFRESH_TOKEN_INVALID));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("careerforge_refresh", "invalid-refresh")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40111))
                .andExpect(cookie().value("careerforge_refresh", ""))
                .andExpect(cookie().maxAge("careerforge_refresh", 0));

        verify(service).refresh("invalid-refresh");
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证Access Token过期后仍能通过Refresh Token退出并清除Cookie
     * @author: Miao Zheng
     * @date: 2026-09-08
     **/
    @Test
    void shouldLogoutWithoutAccessTokenAndClearCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("careerforge_refresh", "current-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(cookie().value("careerforge_refresh", ""))
                .andExpect(cookie().httpOnly("careerforge_refresh", true))
                .andExpect(cookie().path("careerforge_refresh", "/api/auth"))
                .andExpect(cookie().maxAge("careerforge_refresh", 0));

        verify(service).logout("current-refresh");
    }

    private String validAccessToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("careerforge-test")
                .subject(USER_ID.value())
                .audience(List.of("careerforge-api"))
                .issuedAt(now)
                .notBefore(now.minusSeconds(1))
                .expiresAt(now.plusSeconds(900))
                .claim("token_type", "access")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return context.getBean(JwtEncoder.class)
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private AuthApplicationService.AuthResult authResult(String refreshToken) {
        Instant now = Instant.now();
        return new AuthApplicationService.AuthResult(
                USER_ID,
                "user@example.com",
                "User",
                "Bearer",
                "access-token",
                now.plusSeconds(900),
                refreshToken,
                now.plus(Duration.ofDays(30))
        );
    }

    private AuthApplicationService.CurrentUser currentUser() {
        return new AuthApplicationService.CurrentUser(
                USER_ID, "user@example.com", "User",
                Instant.now().minus(Duration.ofDays(1)), Instant.now()
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfiguration.class, AuthApiExceptionHandler.class})
    static class TestConfiguration {

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        AuthProperties authProperties() {
            String secret = Base64.getEncoder().encodeToString(
                    "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
            );
            return new AuthProperties(true, "careerforge-test", "careerforge-api",
                    secret, Duration.ofMinutes(15), Duration.ofDays(30), 32);
        }

        @Bean
        AuthApplicationService authApplicationService() {
            return mock(AuthApplicationService.class);
        }

        @Bean
        RefreshTokenCookieProperties refreshTokenCookieProperties() {
            return new RefreshTokenCookieProperties(
                    "careerforge_refresh",
                    "/api/auth",
                    false,
                    "Strict"
            );
        }

        @Bean
        RefreshTokenCookieManager refreshTokenCookieManager(
                AuthProperties authProperties,
                RefreshTokenCookieProperties cookieProperties) {
            return new RefreshTokenCookieManager(authProperties, cookieProperties);
        }

        @Bean
        AuthController authController(
                AuthApplicationService service,
                AuthLoginRateLimiter loginRateLimiter,
                RefreshTokenCookieManager cookieManager) {
            return new AuthController(service, loginRateLimiter, cookieManager);
        }

        @Bean
        AuthLoginRateLimiter authLoginRateLimiter() {
            return mock(AuthLoginRateLimiter.class);
        }
    }
}