package com.leo.careerforgeai.testsupport;

import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.testsupport.MutableCurrentActorProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ActorBoundaryTest {

    @Test
    void shouldNormalizeActorIdWithoutChangingItsIdentity() {
        ActorId actorId = new ActorId("  actor-a  ");

        assertThat(actorId.value()).isEqualTo("actor-a");
    }

    @Test
    void shouldRejectInvalidActorIds() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ActorId(null));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ActorId("   "));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ActorId("actor\nadmin"));
    }

    @Test
    void shouldSwitchBetweenTwoServerControlledTestActors() {
        MutableCurrentActorProvider provider =
                new MutableCurrentActorProvider(MutableCurrentActorProvider.ACTOR_A);

        assertThat(provider.currentActor())
                .isEqualTo(MutableCurrentActorProvider.ACTOR_A);

        provider.switchTo(MutableCurrentActorProvider.ACTOR_B);

        assertThat(provider.currentActor())
                .isEqualTo(MutableCurrentActorProvider.ACTOR_B);
    }
}