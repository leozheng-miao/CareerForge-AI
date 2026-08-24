package com.leo.careerforgeai.agent.application.run;

import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAdmissionGate;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncDispatcher;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncTask;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunCapacityRejectedException;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunDispatchRejectedException;
import com.leo.careerforgeai.agent.application.run.execution.RunAdmissionLease;
import com.leo.careerforgeai.agent.application.run.execution.RunExecutionContext;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunAcceptanceApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunAsyncSubmissionApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimApplicationService;
import com.leo.careerforgeai.agent.application.run.submission.CoachingRunClaimResult;
import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.leo.careerforgeai.agent.application.port.run.CoachingRunRateLimiter;
import com.leo.careerforgeai.agent.application.run.lifecycle.CoachingRunLifecycleApplicationService;
import com.leo.careerforgeai.agent.application.run.ratelimit.CoachingRunRateLimitDecision;
import com.leo.careerforgeai.agent.application.run.ratelimit.CoachingRunRateLimitExceededException;
import com.leo.careerforgeai.agent.application.run.ratelimit.CoachingRunRateLimitUnavailableException;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureErrorType;
import com.leo.careerforgeai.agent.infrastructure.redis.RedisInfrastructureException;
import static org.mockito.Mockito.never;

/**
 * @program: CareerForge-AI
 * @description: 验证异步Run提交的幂等重放、容量准入、许可移交和失败释放
 * @author: Miao Zheng
 * @date: 2026-08-20
 **/
@ExtendWith(MockitoExtension.class)
class CoachingRunAsyncSubmissionApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("actor-a");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID USER_TURN_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
    private static final String MESSAGE = "请解释Java并发";

    @Mock
    private CurrentActorProvider currentActorProvider;

    @Mock
    private CoachingRunClaimApplicationService claimService;

    @Mock
    private CoachingRunAcceptanceApplicationService acceptanceService;

    @Mock
    private CoachingRunAsyncTask asyncTask;

    @Mock
    private CoachingRunAsyncDispatcher dispatcher;

    @Mock
    private CoachingRunLifecycleApplicationService lifecycleService;

    @Mock
    private CoachingRunRateLimiter rateLimiter;

    private CoachingRunExecutionProperties properties;
    private CoachingRunAsyncSubmissionApplicationService service;

    @BeforeEach
    void setUp() {
        properties = new CoachingRunExecutionProperties(
                2,
                1,
                Duration.ofSeconds(90),
                Duration.ofSeconds(1)
        );
        org.mockito.Mockito.lenient()
                .when(rateLimiter.acquire(OWNER))
                .thenReturn(new CoachingRunRateLimitDecision(
                        true,
                        9,
                        Duration.ofMinutes(1)
                ));
        service = new CoachingRunAsyncSubmissionApplicationService(
                currentActorProvider,
                claimService,
                acceptanceService,
                lifecycleService,
                rateLimiter,
                asyncTask,
                dispatcher,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldAcceptNewRunAndHandLeaseToDispatcher() {
        CoachingRun received = receivedRun();
        CoachingRun accepted = acceptedRun();
        RunAdmissionLease lease = lease();

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(claimService.claim(SESSION_ID, REQUEST_ID, 4L, MESSAGE))
                .thenReturn(new CoachingRunClaimResult(received, false));
        when(dispatcher.acquire(OWNER)).thenReturn(lease);
        when(acceptanceService.accept(RUN_ID, MESSAGE)).thenReturn(accepted);
        when(dispatcher.dispatch(any(RunExecutionContext.class), same(lease), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        CoachingRun result = service.submit(SESSION_ID, REQUEST_ID, 4L, MESSAGE);

        assertThat(result).isSameAs(accepted);

        ArgumentCaptor<RunExecutionContext> contextCaptor =
                ArgumentCaptor.forClass(RunExecutionContext.class);
        verify(dispatcher).dispatch(contextCaptor.capture(), same(lease), any());

        RunExecutionContext context = contextCaptor.getValue();
        assertThat(context.ownerId()).isEqualTo(OWNER);
        assertThat(context.runId()).isEqualTo(RUN_ID);
        assertThat(context.traceId()).isEqualTo(RUN_ID.toString());
        assertThat(context.submittedAt()).isEqualTo(NOW);
        assertThat(context.deadline()).isEqualTo(NOW.plusSeconds(90));
        assertThat(lease.isReleased()).isFalse();
        verifyNoInteractions(asyncTask);

        lease.close();
    }

    @Test
    void shouldReplayAcceptedRunWithoutAcquiringCapacity() {
        CoachingRun accepted = acceptedRun();

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(claimService.claim(SESSION_ID, REQUEST_ID, 4L, MESSAGE))
                .thenReturn(new CoachingRunClaimResult(accepted, true));

        CoachingRun result = service.submit(SESSION_ID, REQUEST_ID, 4L, MESSAGE);

        assertThat(result).isSameAs(accepted);
        verifyNoInteractions(dispatcher, acceptanceService, asyncTask);
    }

    @Test
    void shouldResumeReplayedReceivedRun() {
        CoachingRun received = receivedRun();
        CoachingRun accepted = acceptedRun();
        RunAdmissionLease lease = lease();

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(claimService.claim(SESSION_ID, REQUEST_ID, 4L, MESSAGE))
                .thenReturn(new CoachingRunClaimResult(received, true));
        when(dispatcher.acquire(OWNER)).thenReturn(lease);
        when(acceptanceService.accept(RUN_ID, MESSAGE)).thenReturn(accepted);
        when(dispatcher.dispatch(any(RunExecutionContext.class), same(lease), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        CoachingRun result = service.submit(SESSION_ID, REQUEST_ID, 4L, MESSAGE);

        assertThat(result).isSameAs(accepted);
        verify(acceptanceService).accept(RUN_ID, MESSAGE);
        verify(dispatcher).dispatch(any(RunExecutionContext.class), same(lease), any());

        lease.close();
    }

    @Test
    void shouldRejectBeforeSavingUserTurnWhenCapacityIsFull() {
        CoachingRun received = receivedRun();

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(claimService.claim(SESSION_ID, REQUEST_ID, 4L, MESSAGE))
                .thenReturn(new CoachingRunClaimResult(received, false));
        when(dispatcher.acquire(OWNER))
                .thenThrow(new CoachingRunCapacityRejectedException(OWNER));

        assertThatThrownBy(() -> service.submit(SESSION_ID, REQUEST_ID, 4L, MESSAGE))
                .isInstanceOf(CoachingRunCapacityRejectedException.class)
                .hasMessage("Coaching Run执行容量已满");

        verifyNoInteractions(acceptanceService, asyncTask);
        verify(lifecycleService).rejectForActor(
                OWNER,
                RUN_ID,
                "LOCAL_CAPACITY_REJECTED"
        );
    }

    @Test
    void shouldReleaseLeaseWhenAcceptanceFails() {
        CoachingRun received = receivedRun();
        RunAdmissionLease lease = lease();

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(claimService.claim(SESSION_ID, REQUEST_ID, 4L, MESSAGE))
                .thenReturn(new CoachingRunClaimResult(received, false));
        when(dispatcher.acquire(OWNER)).thenReturn(lease);
        when(acceptanceService.accept(RUN_ID, MESSAGE))
                .thenThrow(new IllegalStateException("Session版本已经变化"));

        assertThatThrownBy(() -> service.submit(SESSION_ID, REQUEST_ID, 4L, MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session版本已经变化");

        assertThat(lease.isReleased()).isTrue();
        verifyNoInteractions(asyncTask);
    }

    @Test
    void shouldRejectAcceptedRunWhenExecutorStopsAccepting() {
        CoachingRun received = receivedRun();
        CoachingRun accepted = acceptedRun();
        RunAdmissionLease lease = lease();
        CoachingRunDispatchRejectedException rejection =
                new CoachingRunDispatchRejectedException(
                        OWNER,
                        RUN_ID,
                        new RejectedExecutionException("executor closed")
                );

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(claimService.claim(SESSION_ID, REQUEST_ID, 4L, MESSAGE))
                .thenReturn(new CoachingRunClaimResult(received, false));
        when(dispatcher.acquire(OWNER)).thenReturn(lease);
        when(acceptanceService.accept(RUN_ID, MESSAGE)).thenReturn(accepted);
        when(dispatcher.dispatch(any(RunExecutionContext.class), same(lease), any()))
                .thenThrow(rejection);

        assertThatThrownBy(() -> service.submit(
                SESSION_ID,
                REQUEST_ID,
                4L,
                MESSAGE
        )).isSameAs(rejection);

        verify(lifecycleService).rejectForActor(
                OWNER,
                RUN_ID,
                "EXECUTOR_NOT_ACCEPTING"
        );
        assertThat(lease.isReleased()).isTrue();
        verifyNoInteractions(asyncTask);
    }

    @Test
    void shouldNotChargeRateLimitForReplayedReceivedRun() {
        CoachingRun received = receivedRun();
        CoachingRun accepted = acceptedRun();
        RunAdmissionLease lease = lease();

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(claimService.claim(SESSION_ID, REQUEST_ID, 4L, MESSAGE))
                .thenReturn(new CoachingRunClaimResult(received, true));
        when(dispatcher.acquire(OWNER)).thenReturn(lease);
        when(acceptanceService.accept(RUN_ID, MESSAGE)).thenReturn(accepted);
        when(dispatcher.dispatch(any(RunExecutionContext.class), same(lease), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        CoachingRun result = service.submit(SESSION_ID, REQUEST_ID, 4L, MESSAGE);

        assertThat(result).isSameAs(accepted);
        verify(rateLimiter, never()).acquire(OWNER);
        lease.close();
    }

    @Test
    void shouldRejectNewRunWhenRateLimitIsExceeded() {
        CoachingRun received = receivedRun();
        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(claimService.claim(SESSION_ID, REQUEST_ID, 4L, MESSAGE))
                .thenReturn(new CoachingRunClaimResult(received, false));
        when(rateLimiter.acquire(OWNER))
                .thenReturn(new CoachingRunRateLimitDecision(
                        false,
                        0,
                        Duration.ofSeconds(13)
                ));

        assertThatThrownBy(() -> service.submit(
                SESSION_ID,
                REQUEST_ID,
                4L,
                MESSAGE
        )).isInstanceOfSatisfying(
                CoachingRunRateLimitExceededException.class,
                exception -> {
                    assertThat(exception.runId()).isEqualTo(RUN_ID);
                    assertThat(exception.retryAfter()).isEqualTo(Duration.ofSeconds(13));
                }
        );

        verify(lifecycleService).rejectForActor(OWNER, RUN_ID, "RATE_LIMITED");
        verifyNoInteractions(dispatcher, acceptanceService, asyncTask);
    }

    @Test
    void shouldFailClosedWhenRedisRateLimitIsUnavailable() {
        CoachingRun received = receivedRun();
        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(claimService.claim(SESSION_ID, REQUEST_ID, 4L, MESSAGE))
                .thenReturn(new CoachingRunClaimResult(received, false));
        when(rateLimiter.acquire(OWNER))
                .thenThrow(new RedisInfrastructureException(
                        RedisInfrastructureErrorType.UNAVAILABLE,
                        "Redis不可用"
                ));

        assertThatThrownBy(() -> service.submit(
                SESSION_ID,
                REQUEST_ID,
                4L,
                MESSAGE
        )).isInstanceOfSatisfying(
                CoachingRunRateLimitUnavailableException.class,
                exception -> assertThat(exception.errorType())
                        .isEqualTo(RedisInfrastructureErrorType.UNAVAILABLE)
        );

        verify(lifecycleService).rejectForActor(
                OWNER,
                RUN_ID,
                "RATE_LIMIT_UNAVAILABLE"
        );
        verifyNoInteractions(dispatcher, acceptanceService, asyncTask);
    }

    private RunAdmissionLease lease() {
        CoachingRunAdmissionGate gate = new CoachingRunAdmissionGate(properties);
        return gate.tryAcquire(OWNER).orElseThrow();
    }

    private CoachingRun receivedRun() {
        return CoachingRun.receive(
                RUN_ID,
                OWNER,
                SESSION_ID,
                REQUEST_ID,
                "a".repeat(64),
                4L,
                NOW.minusSeconds(10)
        );
    }

    private CoachingRun acceptedRun() {
        return receivedRun().accept(USER_TURN_ID, NOW.minusSeconds(5));
    }
}