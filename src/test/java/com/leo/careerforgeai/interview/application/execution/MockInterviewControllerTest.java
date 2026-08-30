package com.leo.careerforgeai.interview.application.execution;

import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAdmissionGate;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncDispatcher;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunCapacityRejectedException;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunDispatchRejectedException;
import com.leo.careerforgeai.agent.application.run.execution.RunAdmissionLease;
import com.leo.careerforgeai.agent.application.run.execution.RunExecutionContext;
import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.interview.application.session.MockInterviewLifecycleApplicationService;
import com.leo.careerforgeai.interview.application.session.MockInterviewVersionConflictException;
import com.leo.careerforgeai.interview.domain.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
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
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description: 验证模拟面试异步启动的状态认领、幂等重放、容量拒绝和移交失败收敛
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@ExtendWith(MockitoExtension.class)
class MockInterviewAsyncSubmissionApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("owner-a");
    private static final UUID INTERVIEW_ID = UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");

    @Mock
    private CurrentActorProvider currentActorProvider;

    @Mock
    private MockInterviewLifecycleApplicationService lifecycleService;

    @Mock
    private MockInterviewAsyncTask asyncTask;

    @Mock
    private CoachingRunAsyncDispatcher dispatcher;

    private CoachingRunExecutionProperties properties;
    private MockInterviewAsyncSubmissionApplicationService service;

    @BeforeEach
    void setUp() {
        properties = new CoachingRunExecutionProperties(
                2,
                1,
                Duration.ofSeconds(90),
                Duration.ofSeconds(1)
        );
        service = new MockInterviewAsyncSubmissionApplicationService(
                currentActorProvider,
                lifecycleService,
                asyncTask,
                dispatcher,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldClaimCreatedInterviewAndHandExecutionToDispatcher() {
        MockInterviewSession created = createdSession();
        MockInterviewSession accepted = created.startQuestionGeneration(NOW.plusSeconds(1));
        RunAdmissionLease lease = lease();

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(lifecycleService.get(INTERVIEW_ID)).thenReturn(created);
        when(dispatcher.acquire(OWNER)).thenReturn(lease);
        when(lifecycleService.startQuestionGeneration(INTERVIEW_ID, 0)).thenReturn(accepted);
        when(dispatcher.dispatch(any(RunExecutionContext.class), same(lease), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        MockInterviewSession result = service.submitStart(INTERVIEW_ID, 0);

        assertThat(result).isSameAs(accepted);
        ArgumentCaptor<RunExecutionContext> contextCaptor = ArgumentCaptor.forClass(RunExecutionContext.class);
        verify(dispatcher).dispatch(contextCaptor.capture(), same(lease), any());

        RunExecutionContext context = contextCaptor.getValue();
        assertThat(context.ownerId()).isEqualTo(OWNER);
        assertThat(context.runId()).isEqualTo(INTERVIEW_ID);
        assertThat(context.traceId()).isEqualTo("interview-" + INTERVIEW_ID);
        assertThat(context.submittedAt()).isEqualTo(NOW);
        assertThat(context.deadline()).isEqualTo(NOW.plusSeconds(90));
        assertThat(lease.isReleased()).isFalse();

        lease.close();
    }

    @Test
    void shouldReturnAlreadyStartedInterviewWithoutSecondDispatch() {
        MockInterviewSession waiting = createdSession()
                .startQuestionGeneration(NOW.plusSeconds(1))
                .waitForAnswer(NOW.plusSeconds(2));

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(lifecycleService.get(INTERVIEW_ID)).thenReturn(waiting);

        MockInterviewSession result = service.submitStart(INTERVIEW_ID, 0);

        assertThat(result).isSameAs(waiting);
        verifyNoInteractions(dispatcher, asyncTask);
        verify(lifecycleService, never()).startQuestionGeneration(any(), anyLong());
    }

    @Test
    void shouldRejectStaleVersionBeforeCapacityAcquisition() {
        MockInterviewSession created = createdSession();

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(lifecycleService.get(INTERVIEW_ID)).thenReturn(created);

        assertThatThrownBy(() -> service.submitStart(INTERVIEW_ID, 1))
                .isInstanceOf(MockInterviewVersionConflictException.class);

        verifyNoInteractions(dispatcher, asyncTask);
        verify(lifecycleService, never()).startQuestionGeneration(any(), anyLong());
    }

    @Test
    void shouldKeepCreatedStateWhenCapacityIsUnavailable() {
        MockInterviewSession created = createdSession();

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(lifecycleService.get(INTERVIEW_ID)).thenReturn(created);
        when(dispatcher.acquire(OWNER)).thenThrow(new CoachingRunCapacityRejectedException(OWNER));

        assertThatThrownBy(() -> service.submitStart(INTERVIEW_ID, 0))
                .isInstanceOf(CoachingRunCapacityRejectedException.class);

        verify(lifecycleService, never()).startQuestionGeneration(any(), anyLong());
        verifyNoInteractions(asyncTask);
    }

    @Test
    void shouldInterruptAcceptedInterviewWhenDispatchIsRejected() {
        MockInterviewSession created = createdSession();
        MockInterviewSession accepted = created.startQuestionGeneration(NOW.plusSeconds(1));
        RunAdmissionLease lease = lease();
        CoachingRunDispatchRejectedException rejection = new CoachingRunDispatchRejectedException(
                OWNER,
                INTERVIEW_ID,
                new RejectedExecutionException("executor closed")
        );

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(lifecycleService.get(INTERVIEW_ID)).thenReturn(created);
        when(dispatcher.acquire(OWNER)).thenReturn(lease);
        when(lifecycleService.startQuestionGeneration(INTERVIEW_ID, 0)).thenReturn(accepted);
        when(dispatcher.dispatch(any(RunExecutionContext.class), same(lease), any())).thenThrow(rejection);

        assertThatThrownBy(() -> service.submitStart(INTERVIEW_ID, 0)).isSameAs(rejection);

        verify(lifecycleService).interrupt(
                INTERVIEW_ID,
                accepted.version(),
                InterviewFailureCode.APPLICATION_SHUTDOWN
        );
        assertThat(lease.isReleased()).isTrue();
        verifyNoInteractions(asyncTask);
    }

    private RunAdmissionLease lease() {
        return new CoachingRunAdmissionGate(properties).tryAcquire(OWNER).orElseThrow();
    }

    private MockInterviewSession createdSession() {
        return MockInterviewSession.create(
                INTERVIEW_ID,
                OWNER,
                UUID.fromString("91000000-0000-0000-0000-000000000002"),
                "a".repeat(64),
                InterviewMode.TARGETED_MOCK,
                UUID.fromString("91000000-0000-0000-0000-000000000003"),
                "b".repeat(64),
                new InterviewBudgetPolicy(5, 2, 20, 20_000),
                NOW
        );
    }
}