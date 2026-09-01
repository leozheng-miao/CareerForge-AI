package com.leo.careerforgeai.interview.application.execution;

import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAdmissionGate;
import com.leo.careerforgeai.agent.application.run.execution.CoachingRunAsyncDispatcher;
import com.leo.careerforgeai.agent.application.run.execution.RunAdmissionLease;
import com.leo.careerforgeai.agent.application.run.execution.RunExecutionContext;
import com.leo.careerforgeai.agent.config.CoachingRunExecutionProperties;
import com.leo.careerforgeai.interview.application.graph.InterviewGraphExecutionService;
import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewLifecycleApplicationService;
import com.leo.careerforgeai.interview.domain.session.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.session.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.session.InterviewMode;
import com.leo.careerforgeai.interview.domain.round.InterviewRound;
import com.leo.careerforgeai.interview.domain.session.InterviewStatus;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description: 验证中断报告只可从安全Checkpoint重新准入并恢复同一面试Graph
 * @author: Miao Zheng
 * @date: 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class MockInterviewReportRetryApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("report-retry-owner");
    private static final UUID INTERVIEW_ID =
            UUID.fromString("92000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-31T05:00:00Z");

    @Mock
    private CurrentActorProvider currentActorProvider;

    @Mock
    private MockInterviewLifecycleApplicationService lifecycleService;

    @Mock
    private InterviewRoundRepository roundRepository;

    @Mock
    private InterviewGraphExecutionService graphExecutionService;

    @Mock
    private MockInterviewAsyncTask asyncTask;

    @Mock
    private CoachingRunAsyncDispatcher dispatcher;

    private CoachingRunExecutionProperties properties;
    private MockInterviewReportRetryApplicationService service;

    @BeforeEach
    void setUp() {
        properties = new CoachingRunExecutionProperties(
                2,
                1,
                Duration.ofSeconds(90),
                Duration.ofSeconds(1)
        );
        service = new MockInterviewReportRetryApplicationService(
                currentActorProvider,
                lifecycleService,
                roundRepository,
                graphExecutionService,
                asyncTask,
                dispatcher,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldReopenInterruptedReportAndDispatchGraphRecovery() {
        MockInterviewSession interrupted = interruptedSession();
        MockInterviewSession accepted = interrupted.retryReportGeneration(NOW);
        InterviewRound reviewedRound = reviewedRound();
        RunAdmissionLease lease = lease();

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(lifecycleService.get(INTERVIEW_ID)).thenReturn(interrupted);
        when(roundRepository.countQuestions(OWNER, INTERVIEW_ID)).thenReturn(1);
        when(roundRepository.findRoundByNumber(OWNER, INTERVIEW_ID, 1))
                .thenReturn(Optional.of(reviewedRound));
        when(dispatcher.acquire(OWNER)).thenReturn(lease);
        when(lifecycleService.retryReportGeneration(INTERVIEW_ID, 17)).thenReturn(accepted);
        when(dispatcher.dispatch(any(RunExecutionContext.class), same(lease), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        MockInterviewSession result = service.submit(INTERVIEW_ID, 17);

        assertThat(result).isSameAs(accepted);
        assertThat(result.status()).isEqualTo(InterviewStatus.GENERATING_REPORT);
        assertThat(result.version()).isEqualTo(18);

        InOrder order = inOrder(
                graphExecutionService,
                roundRepository,
                dispatcher,
                lifecycleService
        );
        order.verify(graphExecutionService).requireReportRecoveryBoundary(INTERVIEW_ID);
        order.verify(roundRepository).countQuestions(OWNER, INTERVIEW_ID);
        order.verify(dispatcher).acquire(OWNER);
        order.verify(lifecycleService).retryReportGeneration(INTERVIEW_ID, 17);

        ArgumentCaptor<RunExecutionContext> contextCaptor =
                ArgumentCaptor.forClass(RunExecutionContext.class);
        verify(dispatcher).dispatch(contextCaptor.capture(), same(lease), any());

        RunExecutionContext context = contextCaptor.getValue();
        assertThat(context.ownerId()).isEqualTo(OWNER);
        assertThat(context.runId()).isEqualTo(INTERVIEW_ID);
        assertThat(context.traceId()).isEqualTo("interview-report-retry-" + INTERVIEW_ID);
        assertThat(context.submittedAt()).isEqualTo(NOW);
        assertThat(context.deadline()).isEqualTo(NOW.plusSeconds(90));
        assertThat(lease.isReleased()).isFalse();

        lease.close();
    }

    @Test
    void shouldRejectUnsafeCheckpointBeforeCapacityAndStateMutation() {
        MockInterviewSession interrupted = interruptedSession();
        IllegalStateException failure =
                new IllegalStateException("Checkpoint不处于可安全重试的报告生成边界");

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(lifecycleService.get(INTERVIEW_ID)).thenReturn(interrupted);
        doThrow(failure)
                .when(graphExecutionService)
                .requireReportRecoveryBoundary(INTERVIEW_ID);

        assertThatThrownBy(() -> service.submit(INTERVIEW_ID, 17)).isSameAs(failure);

        verifyNoInteractions(roundRepository, dispatcher, asyncTask);
        verify(lifecycleService, never()).retryReportGeneration(any(), anyLong());
    }

    private MockInterviewSession interruptedSession() {
        return new MockInterviewSession(
                INTERVIEW_ID,
                OWNER,
                UUID.fromString("92000000-0000-0000-0000-000000000002"),
                "a".repeat(64),
                InterviewMode.TARGETED_MOCK,
                UUID.fromString("92000000-0000-0000-0000-000000000003"),
                "b".repeat(64),
                InterviewStatus.INTERRUPTED,
                new InterviewBudgetPolicy(5, 2, 20, 20_000),
                InterviewFailureCode.INTERNAL_ERROR,
                17,
                NOW.minusSeconds(300),
                NOW.minusSeconds(1),
                NOW.minusSeconds(1)
        );
    }

    private InterviewRound reviewedRound() {
        Instant createdAt = NOW.minusSeconds(60);
        return InterviewRound.questionReady(
                UUID.fromString("92000000-0000-0000-0000-000000000004"),
                INTERVIEW_ID,
                OWNER,
                1,
                createdAt
        ).answer(createdAt.plusSeconds(10))
                .review(createdAt.plusSeconds(20));
    }

    private RunAdmissionLease lease() {
        return new CoachingRunAdmissionGate(properties)
                .tryAcquire(OWNER)
                .orElseThrow();
    }
}