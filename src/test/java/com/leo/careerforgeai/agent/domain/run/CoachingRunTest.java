package com.leo.careerforgeai.agent.domain.run;

import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Coaching Run合法状态迁移、终态字段和版本单调递增
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
class CoachingRunTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID RUN_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID USER_TURN_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ASSISTANT_TURN_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final String FINGERPRINT = "a".repeat(64);
    private static final Instant RECEIVED_AT =
            Instant.parse("2026-08-20T01:00:00Z");
    private static final Instant ACCEPTED_AT =
            Instant.parse("2026-08-20T01:00:01Z");
    private static final Instant STARTED_AT =
            Instant.parse("2026-08-20T01:00:02Z");
    private static final Instant FINISHED_AT =
            Instant.parse("2026-08-20T01:00:03Z");

    @Test
    void shouldProgressFromReceivedToSucceeded() {
        CoachingRun received = received();
        CoachingRun accepted =
                received.accept(USER_TURN_ID, ACCEPTED_AT);
        CoachingRun running = accepted.start(STARTED_AT);
        CoachingRun succeeded =
                running.succeed(ASSISTANT_TURN_ID, FINISHED_AT);

        assertThat(received.status())
                .isEqualTo(CoachingRunStatus.RECEIVED);
        assertThat(accepted.status())
                .isEqualTo(CoachingRunStatus.ACCEPTED);
        assertThat(running.status())
                .isEqualTo(CoachingRunStatus.RUNNING);
        assertThat(succeeded.status())
                .isEqualTo(CoachingRunStatus.SUCCEEDED);
        assertThat(succeeded.version()).isEqualTo(3);
        assertThat(succeeded.userTurnId())
                .isEqualTo(USER_TURN_ID);
        assertThat(succeeded.assistantTurnId())
                .isEqualTo(ASSISTANT_TURN_ID);
        assertThat(succeeded.failureCode()).isNull();
        assertThat(succeeded.isTerminal()).isTrue();
    }

    @Test
    void shouldCreateControlledFailureTerminals() {
        CoachingRun running = received()
                .accept(USER_TURN_ID, ACCEPTED_AT)
                .start(STARTED_AT);

        CoachingRun failed = running.fail(
                ASSISTANT_TURN_ID,
                "MODEL_FAILURE",
                FINISHED_AT
        );
        CoachingRun timedOut = running.timeOut(
                ASSISTANT_TURN_ID,
                "AGENT_DEADLINE_EXCEEDED",
                FINISHED_AT
        );
        CoachingRun rejected = received().reject(
                "CAPACITY_REJECTED",
                FINISHED_AT
        );
        CoachingRun interrupted = running.interrupt(
                "APPLICATION_RESTART",
                FINISHED_AT
        );

        assertThat(failed.status())
                .isEqualTo(CoachingRunStatus.FAILED);
        assertThat(timedOut.status())
                .isEqualTo(CoachingRunStatus.TIMED_OUT);
        assertThat(rejected.status())
                .isEqualTo(CoachingRunStatus.REJECTED);
        assertThat(rejected.userTurnId()).isNull();
        assertThat(interrupted.status())
                .isEqualTo(CoachingRunStatus.INTERRUPTED);
        assertThat(interrupted.assistantTurnId()).isNull();
    }

    @Test
    void shouldRejectSkippedAndTerminalTransitions() {
        CoachingRun received = received();

        assertThatThrownBy(
                () -> received.start(STARTED_AT)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RECEIVED -> RUNNING");

        CoachingRun succeeded = received
                .accept(USER_TURN_ID, ACCEPTED_AT)
                .start(STARTED_AT)
                .succeed(ASSISTANT_TURN_ID, FINISHED_AT);

        assertThatThrownBy(
                () -> succeeded.interrupt(
                        "APPLICATION_RESTART",
                        FINISHED_AT.plusSeconds(1)
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUCCEEDED -> INTERRUPTED");
    }

    private CoachingRun received() {
        return CoachingRun.receive(
                RUN_ID,
                OWNER,
                SESSION_ID,
                REQUEST_ID,
                FINGERPRINT,
                4,
                RECEIVED_AT
        );
    }
}