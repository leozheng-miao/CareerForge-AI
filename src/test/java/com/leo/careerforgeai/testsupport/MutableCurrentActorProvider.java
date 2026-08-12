package com.leo.careerforgeai.testsupport;

import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;

import java.util.Objects;

public final class MutableCurrentActorProvider implements CurrentActorProvider {

    public static final ActorId ACTOR_A = new ActorId("actor-a");
    public static final ActorId ACTOR_B = new ActorId("actor-b");

    private ActorId currentActor;

    public MutableCurrentActorProvider(ActorId initialActor) {
        this.currentActor = Objects.requireNonNull(initialActor, "initialActor must not be null");
    }

    @Override
    public ActorId currentActor() {
        return currentActor;
    }

    public void switchTo(ActorId actorId) {
        this.currentActor = Objects.requireNonNull(actorId, "actorId must not be null");
    }
}