package com.leo.careerforgeai.career.infrastructure.interview;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.application.training.PendingTrainingPlanWriter;
import com.leo.careerforgeai.career.application.training.TrainingPlanGenerationException;
import com.leo.careerforgeai.career.application.training.TrainingPlanGenerationInputReader;
import com.leo.careerforgeai.career.application.training.TrainingPlanGenerator;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.interview.domain.report.InterviewReport;
import com.leo.careerforgeai.interview.domain.report.InterviewReportConfirmation;
import com.leo.careerforgeai.interview.domain.session.MockInterviewInputSnapshot;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证多条已确认训练建议合并生成一个稳定待确认计划并在缺少Gap时拒绝执行
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
class InterviewTrainingPlanSuggestionAdapterTest {

    private static final ActorId OWNER = new ActorId("training-adapter-owner");
    private static final UUID INTERVIEW_ID = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID REPORT_ID = UUID.fromString("71000000-0000-0000-0000-000000000002");
    private static final UUID SNAPSHOT_ID = UUID.fromString("71000000-0000-0000-0000-000000000003");
    private static final UUID GAP_SNAPSHOT_ID = UUID.fromString("71000000-0000-0000-0000-000000000004");
    private static final Instant NOW = Instant.parse("2026-08-30T04:00:00Z");

    private CareerPlanningRepository repository;
    private TrainingPlanGenerationInputReader inputReader;
    private TrainingPlanGenerator generator;
    private PendingTrainingPlanWriter writer;
    private TrainingPlanGenerationInputReader.FixedInput input;
    private TrainingPlanGenerator.GeneratedPlan generatedPlan;
    private AtomicReference<TrainingPlan> storedPlan;
    private InterviewTrainingPlanSuggestionAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(CareerPlanningRepository.class);
        inputReader = mock(TrainingPlanGenerationInputReader.class);
        generator = mock(TrainingPlanGenerator.class);
        writer = mock(PendingTrainingPlanWriter.class);
        input = mock(TrainingPlanGenerationInputReader.FixedInput.class);
        generatedPlan = mock(TrainingPlanGenerator.GeneratedPlan.class);
        storedPlan = new AtomicReference<>();

        SkillGapSnapshot gapSnapshot = mock(SkillGapSnapshot.class);
        when(gapSnapshot.snapshotId()).thenReturn(GAP_SNAPSHOT_ID);
        when(input.ownerId()).thenReturn(OWNER);
        when(input.gapSnapshot()).thenReturn(gapSnapshot);
        when(inputReader.read(GAP_SNAPSHOT_ID)).thenReturn(input);
        when(repository.findTrainingPlan(eq(OWNER), any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(storedPlan.get()));
        when(generator.generate(eq(input), any())).thenReturn(generatedPlan);
        when(writer.save(eq(input), eq(generatedPlan), any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID planId = invocation.getArgument(2);
                    TrainingPlan plan = mock(TrainingPlan.class);
                    when(plan.planId()).thenReturn(planId);
                    when(plan.ownerId()).thenReturn(OWNER);
                    when(plan.gapSnapshotId()).thenReturn(GAP_SNAPSHOT_ID);
                    when(plan.generationContext())
                            .thenReturn(mock(TrainingPlan.GenerationContext.class));
                    when(plan.status())
                            .thenReturn(TrainingPlan.PlanStatus.PENDING_CONFIRMATION);
                    storedPlan.set(plan);
                    return plan;
                });

        adapter = new InterviewTrainingPlanSuggestionAdapter(
                repository, inputReader, generator, writer
        );
    }

    @Test
    void shouldMergeAdjustmentsIntoOnePlanAndReplayWithoutCallingModelAgain() {
        InterviewReport report = report();
        List<InterviewReportConfirmation.Decision> decisions = decisions(report);

        UUID first = adapter.apply(report, inputSnapshot(GAP_SNAPSHOT_ID), decisions);
        UUID replay = adapter.apply(report, inputSnapshot(GAP_SNAPSHOT_ID), decisions);

        assertThat(replay).isEqualTo(first);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TrainingPlanGenerator.AdjustmentConstraint>> constraintsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(generator, times(1)).generate(eq(input), constraintsCaptor.capture());
        verify(writer, times(1)).save(eq(input), eq(generatedPlan), eq(first));

        assertThat(constraintsCaptor.getValue())
                .extracting(TrainingPlanGenerator.AdjustmentConstraint::focusArea)
                .containsExactly("结构化并发", "线程安全");
        assertThat(constraintsCaptor.getValue())
                .extracting(TrainingPlanGenerator.AdjustmentConstraint::adjustment)
                .containsExactly(
                        "增加取消传播训练。",
                        "增加竞态条件复现实验。"
                );
    }

    @Test
    void shouldRejectTrainingApplicationWithoutFrozenGapSnapshot() {
        InterviewReport report = report();

        assertThatThrownBy(() -> adapter.apply(
                report,
                inputSnapshot(null),
                decisions(report)
        )).isInstanceOfSatisfying(
                TrainingPlanGenerationException.class,
                exception -> assertThat(exception.getErrorType()).isEqualTo(
                        TrainingPlanGenerationException.ErrorType.GAP_SNAPSHOT_NOT_FOUND
                )
        );

        verifyNoInteractions(repository, inputReader, generator, writer);
    }

    private InterviewReport report() {
        InterviewReport.TrainingPlanAdjustmentPayload first =
                new InterviewReport.TrainingPlanAdjustmentPayload(
                        "结构化并发",
                        "增加取消传播训练。"
                );
        InterviewReport.TrainingPlanAdjustmentPayload second =
                new InterviewReport.TrainingPlanAdjustmentPayload(
                        "线程安全",
                        "增加竞态条件复现实验。"
                );

        return InterviewReport.pendingConfirmation(
                REPORT_ID,
                INTERVIEW_ID,
                OWNER,
                List.of("优势"),
                List.of("差距"),
                List.of(),
                List.of("改进动作"),
                List.of(
                        suggestion(
                                UUID.fromString("72000000-0000-0000-0000-000000000001"),
                                1,
                                first,
                                "a".repeat(64)
                        ),
                        suggestion(
                                UUID.fromString("72000000-0000-0000-0000-000000000002"),
                                2,
                                second,
                                "b".repeat(64)
                        )
                ),
                "report-request",
                "report-coach-v2",
                "c".repeat(64),
                "d".repeat(64),
                NOW.minusSeconds(60)
        );
    }

    private InterviewReport.Suggestion suggestion(
            UUID suggestionId,
            int order,
            InterviewReport.TrainingPlanAdjustmentPayload payload,
            String contentHash
    ) {
        return new InterviewReport.Suggestion(
                suggestionId,
                REPORT_ID,
                INTERVIEW_ID,
                OWNER,
                InterviewReport.SuggestionType.TRAINING_PLAN_ADJUSTMENT,
                order,
                payload.displayContent(),
                payload,
                contentHash,
                NOW.minusSeconds(60)
        );
    }

    private List<InterviewReportConfirmation.Decision> decisions(
            InterviewReport report
    ) {
        UUID confirmationId =
                UUID.fromString("73000000-0000-0000-0000-000000000001");

        return List.of(
                confirmedDecision(
                        UUID.fromString("73000000-0000-0000-0000-000000000002"),
                        confirmationId,
                        report.suggestions().getFirst().suggestionId()
                ),
                confirmedDecision(
                        UUID.fromString("73000000-0000-0000-0000-000000000003"),
                        confirmationId,
                        report.suggestions().get(1).suggestionId()
                )
        );
    }

    private InterviewReportConfirmation.Decision confirmedDecision(
            UUID decisionId,
            UUID confirmationId,
            UUID suggestionId
    ) {
        return InterviewReportConfirmation.Decision.confirmed(
                decisionId,
                confirmationId,
                suggestionId,
                REPORT_ID,
                INTERVIEW_ID,
                OWNER,
                NOW.minusSeconds(30)
        );
    }

    private MockInterviewInputSnapshot inputSnapshot(UUID gapSnapshotId) {
        return new MockInterviewInputSnapshot(
                SNAPSHOT_ID,
                OWNER,
                MockInterviewInputSnapshot.CURRENT_SCHEMA_VERSION,
                UUID.fromString("74000000-0000-0000-0000-000000000001"),
                1,
                gapSnapshotId,
                null,
                null,
                "{\"targetRole\":\"Java AI应用开发工程师\"}",
                List.of(new MockInterviewInputSnapshot.ArtifactReference(
                        UUID.fromString("74000000-0000-0000-0000-000000000002"),
                        1,
                        "e".repeat(64),
                        1
                )),
                "f".repeat(64),
                NOW.minusSeconds(120)
        );
    }
}