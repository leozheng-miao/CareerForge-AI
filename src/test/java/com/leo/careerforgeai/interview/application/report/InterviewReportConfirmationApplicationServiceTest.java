package com.leo.careerforgeai.interview.application.report;

import com.leo.careerforgeai.interview.application.port.InterviewMemorySuggestionApplicationPort;
import com.leo.careerforgeai.interview.application.port.InterviewReportConfirmationRepository;
import com.leo.careerforgeai.interview.application.port.InterviewReportRepository;
import com.leo.careerforgeai.interview.application.port.InterviewTrainingPlanSuggestionApplicationPort;
import com.leo.careerforgeai.interview.application.port.MockInterviewInputSnapshotRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.report.confirmation.InterviewReportConfirmationApplicationService;
import com.leo.careerforgeai.interview.application.report.confirmation.InterviewReportConfirmationFactory;
import com.leo.careerforgeai.interview.domain.session.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.session.InterviewMode;
import com.leo.careerforgeai.interview.domain.report.InterviewReport;
import com.leo.careerforgeai.interview.domain.report.InterviewReportConfirmation;
import com.leo.careerforgeai.interview.domain.session.InterviewStatus;
import com.leo.careerforgeai.interview.domain.session.MockInterviewInputSnapshot;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @program: CareerForge-AI
 * @description: 验证报告确认的Memory和训练计划应用、部分失败、最终状态迁移及重放幂等
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
class InterviewReportConfirmationApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("confirmation-owner");
    private static final UUID INTERVIEW_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REPORT_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID SNAPSHOT_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID GAP_SNAPSHOT_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID MEMORY_ID = UUID.fromString("10000000-0000-0000-0000-000000000005");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000006");
    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
    private static final ActorId OTHER_OWNER = new ActorId("other-confirmation-owner");

    private CurrentActorProvider currentActorProvider;
    private InterviewReportRepository reportRepository;
    private InterviewReportConfirmationRepository confirmationRepository;
    private MockInterviewSessionRepository sessionRepository;
    private MockInterviewInputSnapshotRepository inputSnapshotRepository;
    private InterviewMemorySuggestionApplicationPort memoryPort;
    private InterviewTrainingPlanSuggestionApplicationPort trainingPlanPort;
    private AtomicReference<InterviewReport> reportState;
    private AtomicReference<InterviewReportConfirmation> confirmationState;
    private AtomicReference<MockInterviewSession> sessionState;
    private InterviewReport report;
    private MockInterviewInputSnapshot inputSnapshot;
    private InterviewReportConfirmationApplicationService service;

    @BeforeEach
    void setUp() {
        currentActorProvider = mock(CurrentActorProvider.class);
        reportRepository = mock(InterviewReportRepository.class);
        confirmationRepository = mock(InterviewReportConfirmationRepository.class);
        sessionRepository = mock(MockInterviewSessionRepository.class);
        inputSnapshotRepository = mock(MockInterviewInputSnapshotRepository.class);
        memoryPort = mock(InterviewMemorySuggestionApplicationPort.class);
        trainingPlanPort = mock(InterviewTrainingPlanSuggestionApplicationPort.class);

        report = report();
        inputSnapshot = inputSnapshot(GAP_SNAPSHOT_ID);
        reportState = new AtomicReference<>(report);
        confirmationState = new AtomicReference<>(confirmation(report));
        sessionState = new AtomicReference<>(session());

        when(currentActorProvider.currentActor()).thenReturn(OWNER);
        when(reportRepository.findById(OWNER, INTERVIEW_ID, REPORT_ID))
                .thenAnswer(invocation -> Optional.of(reportState.get()));
        when(confirmationRepository.findByReport(OWNER, INTERVIEW_ID, REPORT_ID))
                .thenAnswer(invocation -> Optional.of(confirmationState.get()));
        when(sessionRepository.findById(OWNER, INTERVIEW_ID))
                .thenAnswer(invocation -> Optional.of(sessionState.get()));
        when(inputSnapshotRepository.findById(OWNER, SNAPSHOT_ID))
                .thenReturn(Optional.of(inputSnapshot));

        when(reportRepository.updateIfVersionMatches(
                eq(OWNER), any(InterviewReport.class), anyLong()
        )).thenAnswer(invocation -> {
            InterviewReport updated = invocation.getArgument(1);
            long expectedVersion = invocation.getArgument(2);
            if (reportState.get().version() != expectedVersion) return false;
            reportState.set(updated);
            return true;
        });
        when(confirmationRepository.updateIfVersionMatches(
                eq(OWNER), any(InterviewReportConfirmation.class), anyLong()
        )).thenAnswer(invocation -> {
            InterviewReportConfirmation updated = invocation.getArgument(1);
            long expectedVersion = invocation.getArgument(2);
            if (confirmationState.get().version() != expectedVersion) return false;
            confirmationState.set(updated);
            return true;
        });
        when(sessionRepository.updateIfVersionMatches(
                eq(OWNER), any(MockInterviewSession.class), anyLong()
        )).thenAnswer(invocation -> {
            MockInterviewSession updated = invocation.getArgument(1);
            long expectedVersion = invocation.getArgument(2);
            if (sessionState.get().version() != expectedVersion) return false;
            sessionState.set(updated);
            return true;
        });

        service = new InterviewReportConfirmationApplicationService(
                currentActorProvider,
                reportRepository,
                confirmationRepository,
                sessionRepository,
                inputSnapshotRepository,
                memoryPort,
                trainingPlanPort,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldApplyMemoryAndTrainingPlanThenReplayWithoutDuplicateWrites() {
        when(memoryPort.apply(any(), any())).thenReturn(MEMORY_ID);
        when(trainingPlanPort.apply(any(), any(), any())).thenReturn(PLAN_ID);

        InterviewReportConfirmation first = service.apply(INTERVIEW_ID, REPORT_ID);
        InterviewReportConfirmation replay = service.apply(INTERVIEW_ID, REPORT_ID);

        assertThat(first.status()).isEqualTo(InterviewReportConfirmation.Status.APPLIED);
        assertThat(replay).isEqualTo(first);
        assertThat(first.decisions()).allSatisfy(decision ->
                assertThat(decision.applicationStatus())
                        .isEqualTo(InterviewReportConfirmation.ApplicationStatus.APPLIED)
        );
        assertThat(decisionFor(first, report.suggestions().getFirst().suggestionId()).outputReferenceId())
                .isEqualTo(MEMORY_ID);
        assertThat(decisionFor(first, report.suggestions().get(1).suggestionId()).outputReferenceId())
                .isEqualTo(PLAN_ID);
        assertThat(reportState.get().status()).isEqualTo(InterviewReport.Status.DECIDED);
        assertThat(sessionState.get().status()).isEqualTo(InterviewStatus.COMPLETED);

        verify(memoryPort, times(1)).apply(any(), any());
        verify(trainingPlanPort, times(1)).apply(any(), any(), any());
    }

    @Test
    void shouldRetainPartialFailureAndStillFinishInterview() {
        when(memoryPort.apply(any(), any())).thenThrow(new IllegalStateException("模拟Memory失败"));
        when(trainingPlanPort.apply(any(), any(), any())).thenReturn(PLAN_ID);

        InterviewReportConfirmation result = service.apply(INTERVIEW_ID, REPORT_ID);

        InterviewReportConfirmation.Decision memoryDecision =
                decisionFor(result, report.suggestions().getFirst().suggestionId());
        InterviewReportConfirmation.Decision trainingDecision =
                decisionFor(result, report.suggestions().get(1).suggestionId());

        assertThat(result.status())
                .isEqualTo(InterviewReportConfirmation.Status.PARTIALLY_APPLIED);
        assertThat(result.failureCode()).isEqualTo("SUGGESTION_APPLICATION_FAILED");
        assertThat(memoryDecision.applicationStatus())
                .isEqualTo(InterviewReportConfirmation.ApplicationStatus.FAILED);
        assertThat(memoryDecision.failureCode()).isEqualTo("MEMORY_APPLICATION_FAILED");
        assertThat(trainingDecision.applicationStatus())
                .isEqualTo(InterviewReportConfirmation.ApplicationStatus.APPLIED);
        assertThat(trainingDecision.outputReferenceId()).isEqualTo(PLAN_ID);
        assertThat(reportState.get().status()).isEqualTo(InterviewReport.Status.DECIDED);
        assertThat(sessionState.get().status()).isEqualTo(InterviewStatus.COMPLETED);
    }

    @Test
    void shouldRejectOtherOwnerBeforeReadingReportOrApplyingSuggestions() {
        when(currentActorProvider.currentActor()).thenReturn(OTHER_OWNER);
        when(sessionRepository.findById(OTHER_OWNER, INTERVIEW_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply(INTERVIEW_ID, REPORT_ID))
                .isInstanceOf(MockInterviewNotFoundException.class);

        verify(reportRepository, never()).findById(any(), any(), any());
        verifyNoInteractions(
                confirmationRepository,
                inputSnapshotRepository,
                memoryPort,
                trainingPlanPort
        );
    }

    private InterviewReport report() {
        InterviewReport.MemoryCandidatePayload memoryPayload =
                new InterviewReport.MemoryCandidatePayload(
                        "Java并发",
                        "保留Java并发实践作为长期能力证据。"
                );
        InterviewReport.TrainingPlanAdjustmentPayload trainingPayload =
                new InterviewReport.TrainingPlanAdjustmentPayload(
                        "结构化并发",
                        "增加结构化并发取消传播专项训练。"
                );

        List<InterviewReport.Suggestion> suggestions = List.of(
                new InterviewReport.Suggestion(
                        UUID.fromString("20000000-0000-0000-0000-000000000001"),
                        REPORT_ID,
                        INTERVIEW_ID,
                        OWNER,
                        InterviewReport.SuggestionType.MEMORY_CANDIDATE,
                        1,
                        memoryPayload.content(),
                        memoryPayload,
                        "a".repeat(64),
                        NOW.minusSeconds(120)
                ),
                new InterviewReport.Suggestion(
                        UUID.fromString("20000000-0000-0000-0000-000000000002"),
                        REPORT_ID,
                        INTERVIEW_ID,
                        OWNER,
                        InterviewReport.SuggestionType.TRAINING_PLAN_ADJUSTMENT,
                        1,
                        trainingPayload.adjustment(),
                        trainingPayload,
                        "b".repeat(64),
                        NOW.minusSeconds(120)
                )
        );

        return InterviewReport.pendingConfirmation(
                REPORT_ID,
                INTERVIEW_ID,
                OWNER,
                List.of("能够解释虚拟线程适用边界。"),
                List.of("结构化并发取消传播理解不足。"),
                List.of(),
                List.of("补充取消传播实验。"),
                suggestions,
                "report-request-1",
                "report-coach-v2",
                "c".repeat(64),
                "d".repeat(64),
                NOW.minusSeconds(120)
        );
    }

    private InterviewReportConfirmation confirmation(InterviewReport sourceReport) {
        InterviewReportConfirmationFactory factory =
                new InterviewReportConfirmationFactory(JsonMapper.builder().build());

        return factory.create(
                sourceReport,
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                sourceReport.version(),
                sourceReport.suggestions().stream()
                        .map(suggestion -> new InterviewReportConfirmationFactory.Selection(
                                suggestion.suggestionId(),
                                InterviewReportConfirmation.DecisionType.CONFIRMED
                        ))
                        .toList(),
                NOW.minusSeconds(60)
        );
    }

    private MockInterviewSession session() {
        return new MockInterviewSession(
                INTERVIEW_ID,
                OWNER,
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                "e".repeat(64),
                InterviewMode.TARGETED_MOCK,
                SNAPSHOT_ID,
                "f".repeat(64),
                InterviewStatus.AWAITING_CONFIRMATION,
                new InterviewBudgetPolicy(10, 4, 30, 50_000),
                null,
                6,
                NOW.minusSeconds(600),
                NOW.minusSeconds(30),
                null
        );
    }

    private MockInterviewInputSnapshot inputSnapshot(UUID gapSnapshotId) {
        return new MockInterviewInputSnapshot(
                SNAPSHOT_ID,
                OWNER,
                MockInterviewInputSnapshot.CURRENT_SCHEMA_VERSION,
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                1,
                gapSnapshotId,
                null,
                null,
                "{\"targetRole\":\"Java AI应用开发工程师\"}",
                List.of(new MockInterviewInputSnapshot.ArtifactReference(
                        UUID.fromString("50000000-0000-0000-0000-000000000002"),
                        1,
                        "1".repeat(64),
                        1
                )),
                "f".repeat(64),
                NOW.minusSeconds(600)
        );
    }

    private InterviewReportConfirmation.Decision decisionFor(
            InterviewReportConfirmation confirmation,
            UUID suggestionId
    ) {
        return confirmation.decisions().stream()
                .filter(decision -> decision.suggestionId().equals(suggestionId))
                .findFirst()
                .orElseThrow();
    }
}