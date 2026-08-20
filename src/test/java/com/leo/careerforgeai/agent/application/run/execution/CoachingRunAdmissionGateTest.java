package com.leo.careerforgeai.agent.application.run.execution;

import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证全局和owner Run许可的拒绝、释放及重复关闭安全性
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
class CoachingRunAdmissionGateTest {

    private static final ActorId OWNER_A = new ActorId("actor-a");
    private static final ActorId OWNER_B = new ActorId("actor-b");
    private static final ActorId OWNER_C = new ActorId("actor-c");

    @Test
    void shouldEnforceOwnerAndGlobalCapacity() {
        CoachingRunAdmissionGate controller =
                new CoachingRunAdmissionGate(properties());

        RunAdmissionLease ownerA =
                controller.tryAcquire(OWNER_A).orElseThrow();

        assertThat(controller.tryAcquire(OWNER_A)).isEmpty();

        RunAdmissionLease ownerB =
                controller.tryAcquire(OWNER_B).orElseThrow();

        assertThat(controller.tryAcquire(OWNER_C)).isEmpty();

        ownerA.close();

        RunAdmissionLease ownerC =
                controller.tryAcquire(OWNER_C).orElseThrow();

        ownerB.close();
        ownerC.close();
    }

    @Test
    void shouldReleaseLeaseOnlyOnce() {
        CoachingRunAdmissionGate controller =
                new CoachingRunAdmissionGate(properties());

        RunAdmissionLease first =
                controller.tryAcquire(OWNER_A).orElseThrow();

        first.close();
        first.close();

        Optional<RunAdmissionLease> reacquired =
                controller.tryAcquire(OWNER_A);

        assertThat(reacquired).isPresent();
        reacquired.orElseThrow().close();
    }

    private CoachingRunExecutionProperties properties() {
        return new CoachingRunExecutionProperties(
                2,
                1,
                Duration.ofSeconds(1)
        );
    }
}