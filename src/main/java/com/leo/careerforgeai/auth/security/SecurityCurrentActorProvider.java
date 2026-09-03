package com.leo.careerforgeai.auth.security;

import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import com.leo.careerforgeai.shared.exception.BusinessException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * @program: CareerForge-AI
 * @description: 从已验证的Spring Security JWT认证上下文读取正式用户身份
 * @author: Miao Zheng
 * @date: 2026-09-02
 **/
@Component
@ConditionalOnProperty(prefix = "careerforge.auth", name = "enabled", havingValue = "true")
public class SecurityCurrentActorProvider implements CurrentActorProvider {

    @Override
    public ActorId currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return new ActorId(jwt.getSubject());
    }
}