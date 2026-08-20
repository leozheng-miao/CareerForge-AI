package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.application.port.run.CoachingRunRepository;
import com.leo.careerforgeai.agent.domain.run.CoachingRun;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证新Run认领、相同指纹重放和相同requestId不同指纹冲突
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@ExtendWith(MockitoExtension.class)
class CoachingRunClaimApplicationServiceTest {

    private static final ActorId OWNER =
            new ActorId("actor-a");
    private static final UUID SESSION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID EXISTING_RUN_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW =
            Instant.parse("2026-08-20T02:00:00Z");
    private static final String MESSAGE = "请解释Java并发";

    @Mock
    private CurrentActorProvider currentActorProvider;

    @Mock
    private CoachingRunRepository repository;

    private CoachingRunRequestFingerprintService fingerprintService;
    private CoachingRunClaimApplicationService service;

    @BeforeEach
    void setUp() {
        fingerprintService =
                new CoachingRunRequestFingerprintService();
        service = new CoachingRunClaimApplicationService(
                currentActorProvider,
                repository,
                fingerprintService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(currentActorProvider.currentActor())
                .thenReturn(OWNER);
    }

    @Test
    void shouldClaimNewRun() {
        when(repository.claim(any(CoachingRun.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        CoachingRunClaimResult result = service.claim(
                SESSION_ID,
                REQUEST_ID,
                4,
                MESSAGE
        );

        assertThat(result.replayed()).isFalse();
        assertThat(result.run().ownerId()).isEqualTo(OWNER);
        assertThat(result.run().sessionId())
                .isEqualTo(SESSION_ID);
        assertThat(result.run().requestId())
                .isEqualTo(REQUEST_ID);

        ArgumentCaptor<CoachingRun> captor =
                ArgumentCaptor.forClass(CoachingRun.class);
        verify(repository).claim(captor.capture());
        assertThat(captor.getValue().version()).isZero();
    }

    @Test
    void shouldReplayExistingRunWithSameFingerprint() {
        CoachingRun existing = existingRun(
                fingerprintService.fingerprint(
                        SESSION_ID,
                        4,
                        MESSAGE
                )
        );
        when(repository.claim(any(CoachingRun.class)))
                .thenReturn(existing);

        CoachingRunClaimResult result = service.claim(
                SESSION_ID,
                REQUEST_ID,
                4,
                MESSAGE
        );

        assertThat(result.replayed()).isTrue();
        assertThat(result.run()).isSameAs(existing);
    }

    @Test
    void shouldRejectSameRequestIdWithDifferentFingerprint() {
        CoachingRun existing = existingRun("b".repeat(64));
        when(repository.claim(any(CoachingRun.class)))
                .thenReturn(existing);

        assertThatThrownBy(() -> service.claim(
                SESSION_ID,
                REQUEST_ID,
                4,
                MESSAGE
        ))
                .isInstanceOf(
                        CoachingRunRequestConflictException.class
                )
                .hasMessage("requestId已被用于不同请求");

        assertThat(existing.requestFingerprint())
                .isEqualTo("b".repeat(64));
    }

    private CoachingRun existingRun(String fingerprint) {
        return CoachingRun.receive(
                EXISTING_RUN_ID,
                OWNER,
                SESSION_ID,
                REQUEST_ID,
                fingerprint,
                4,
                NOW.minusSeconds(10)
        );
    }
}