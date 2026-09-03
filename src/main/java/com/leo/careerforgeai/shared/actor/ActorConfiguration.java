package com.leo.careerforgeai.shared.actor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * @program: CareerForge-AI
 * @description: 仅在local、dev、test且正式认证关闭时提供Fixed Actor
 * @author: Miao Zheng
 * @date: 2026-09-02
 **/
@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev", "test"})
@ConditionalOnProperty(
        prefix = "careerforge.auth",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class ActorConfiguration {

    @Bean
    public CurrentActorProvider currentActorProvider(ActorProperties properties) {
        ActorId fixedActor = properties.actorId();
        return () -> fixedActor;
    }
}