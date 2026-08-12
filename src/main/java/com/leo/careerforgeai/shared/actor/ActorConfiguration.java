package com.leo.careerforgeai.shared.actor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ActorConfiguration {

    @Bean
    public CurrentActorProvider currentActorProvider(ActorProperties properties) {
        ActorId fixedActor = properties.actorId();
        return () -> fixedActor;
    }
}