package com.leo.careerforgeai.shared.actor;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "careerforge.actor", ignoreUnknownFields = false)
public record ActorProperties(String fixedId) {

    public ActorProperties {
        fixedId = new ActorId(fixedId).value();
    }

    public ActorId actorId() {
        return new ActorId(fixedId);
    }
}