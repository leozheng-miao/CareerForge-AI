package com.leo.careerforgeai.auth.security;

import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.exception.BusinessException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * @program: CareerForge-AI
 * @description: 验证正式CurrentActorProvider只信任已验证JWT认证上下文
 * @author: Miao Zheng
 * @date: 2026-09-03
 **/
class SecurityCurrentActorProviderTest {

    private final SecurityCurrentActorProvider provider = new SecurityCurrentActorProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证当前用户ID只能来自JWT subject
     * @author: Miao Zheng
     * @date: 2026-09-03
     **/
    @Test
    void shouldReadActorIdFromJwtSubject() {
        Jwt jwt = Jwt.withTokenValue("verified-token")
                .header("alg", "HS256")
                .subject("authenticated-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of())
        );
        assertThat(provider.currentActor()).isEqualTo(new ActorId("authenticated-user"));
    }

    /**
     * @program: CareerForge-AI
     * @description: 验证缺失认证及非JWT伪造身份均不能成为当前用户
     * @author: Miao Zheng
     * @date: 2026-09-03
     **/
    @Test
    void shouldRejectMissingOrNonJwtAuthentication() {
        assertNotLoggedIn();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("spoofed-user", "n/a", List.of())
        );
        assertNotLoggedIn();
    }

    private void assertNotLoggedIn() {
        BusinessException exception = catchThrowableOfType(provider::currentActor, BusinessException.class);
        assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_LOGIN_ERROR.getCode());
    }
}