package com.leo.careerforgeai.shared.actor;

@FunctionalInterface
public interface CurrentActorProvider {

    ActorId currentActor();
}