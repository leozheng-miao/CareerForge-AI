package com.leo.careerforgeai.career.infrastructure.interview;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.application.training.PendingTrainingPlanWriter;
import com.leo.careerforgeai.career.application.training.TrainingPlanGenerationException;
import com.leo.careerforgeai.career.application.training.TrainingPlanGenerationInputReader;
import com.leo.careerforgeai.career.application.training.TrainingPlanGenerator;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.interview.application.port.InterviewTrainingPlanSuggestionApplicationPort;
import com.leo.careerforgeai.interview.domain.report.InterviewReport;
import com.leo.careerforgeai.interview.domain.report.InterviewReportConfirmation;
import com.leo.careerforgeai.interview.domain.session.MockInterviewInputSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 将已确认面试训练建议合并为一次受控训练计划生成并返回稳定计划引用
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
@Slf4j
@Component
@ConditionalOnBean({
        CareerPlanningRepository.class,
        TrainingPlanGenerationInputReader.class,
        TrainingPlanGenerator.class,
        PendingTrainingPlanWriter.class
})
public class InterviewTrainingPlanSuggestionAdapter
        implements InterviewTrainingPlanSuggestionApplicationPort {

    private static final String ID_NAMESPACE = "interview-training-plan-v1";

    private final CareerPlanningRepository repository;
    private final TrainingPlanGenerationInputReader inputReader;
    private final TrainingPlanGenerator generator;
    private final PendingTrainingPlanWriter writer;

    public InterviewTrainingPlanSuggestionAdapter(
            CareerPlanningRepository repository,
            TrainingPlanGenerationInputReader inputReader,
            TrainingPlanGenerator generator,
            PendingTrainingPlanWriter writer
    ) {
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.inputReader = Objects.requireNonNull(inputReader, "inputReader不能为空");
        this.generator = Objects.requireNonNull(generator, "generator不能为空");
        this.writer = Objects.requireNonNull(writer, "writer不能为空");
    }

    @Override
    public UUID apply(
            InterviewReport report,
            MockInterviewInputSnapshot inputSnapshot,
            List<InterviewReportConfirmation.Decision> decisions
    ) {
        Objects.requireNonNull(report, "report不能为空");
        Objects.requireNonNull(inputSnapshot, "inputSnapshot不能为空");
        Objects.requireNonNull(decisions, "decisions不能为空");

        if (!report.ownerId().equals(inputSnapshot.ownerId())) {
            throw new IllegalArgumentException("报告与面试输入快照owner不一致");
        }
        UUID gapSnapshotId = inputSnapshot.skillGapSnapshotId();
        if (gapSnapshotId == null) {
            log.warn(
                    "面试训练计划建议不可应用，reportId={}, inputSnapshotId={}, errorType={}",
                    report.reportId(),
                    inputSnapshot.inputSnapshotId(),
                    TrainingPlanGenerationException.ErrorType.GAP_SNAPSHOT_NOT_FOUND
            );
            throw new TrainingPlanGenerationException(
                    TrainingPlanGenerationException.ErrorType.GAP_SNAPSHOT_NOT_FOUND,
                    "当前面试没有冻结能力差距快照，不能生成训练计划版本"
            );
        }

        Map<UUID, InterviewReportConfirmation.Decision> decisionsBySuggestion =
                indexDecisions(report, decisions);
        List<InterviewReport.Suggestion> suggestions = report.suggestions().stream()
                .filter(suggestion -> decisionsBySuggestion.containsKey(suggestion.suggestionId()))
                .sorted(Comparator.comparingInt(InterviewReport.Suggestion::order))
                .toList();

        if (suggestions.size() != decisionsBySuggestion.size()) {
            throw new IllegalArgumentException("训练计划决定引用了不存在的报告建议");
        }

        List<TrainingPlanGenerator.AdjustmentConstraint> constraints =
                createConstraints(report, suggestions);
        UUID planId = stablePlanId(report, constraints);
        requireAppliedOutputs(decisions, planId);

        TrainingPlan existing = repository.findTrainingPlan(report.ownerId(), planId).orElse(null);
        if (existing != null) {
            return requireReplay(existing, report, gapSnapshotId, planId).planId();
        }
        TrainingPlanGenerationInputReader.FixedInput input = inputReader.read(gapSnapshotId);
        if (!input.ownerId().equals(report.ownerId())
                || !input.gapSnapshot().snapshotId().equals(gapSnapshotId)) {
            throw new IllegalStateException("训练计划固定输入违反报告owner或Gap边界");
        }

        try {
            TrainingPlanGenerator.GeneratedPlan generatedPlan =
                    generator.generate(input, constraints);
            TrainingPlan plan = writer.save(input, generatedPlan, planId);
            return requireReplay(plan, report, gapSnapshotId, planId).planId();
        } catch (RuntimeException exception) {
            TrainingPlan replay = repository.findTrainingPlan(
                    report.ownerId(), planId
            ).orElse(null);
            if (replay != null) {
                return requireReplay(replay, report, gapSnapshotId, planId).planId();
            }
            throw exception;
        }
    }

    private Map<UUID, InterviewReportConfirmation.Decision> indexDecisions(
            InterviewReport report,
            List<InterviewReportConfirmation.Decision> decisions
    ) {
        if (decisions.isEmpty() || decisions.size() > 10) {
            throw new IllegalArgumentException("训练计划确认决定数量必须在1到10之间");
        }

        Map<UUID, InterviewReportConfirmation.Decision> indexed = new LinkedHashMap<>();
        for (InterviewReportConfirmation.Decision decision : decisions) {
            Objects.requireNonNull(decision, "decisions不能包含空值");
            if (!decision.reportId().equals(report.reportId())
                    || !decision.interviewId().equals(report.interviewId())
                    || !decision.ownerId().equals(report.ownerId())) {
                throw new IllegalArgumentException("训练计划决定与报告作用域不一致");
            }
            if (decision.decisionType()
                    != InterviewReportConfirmation.DecisionType.CONFIRMED) {
                throw new IllegalArgumentException("只有用户确认的训练建议可以生成计划");
            }
            if (decision.applicationStatus()
                    != InterviewReportConfirmation.ApplicationStatus.PENDING
                    && decision.applicationStatus()
                    != InterviewReportConfirmation.ApplicationStatus.APPLIED) {
                throw new IllegalStateException("训练计划决定不在可应用或可重放状态");
            }
            if (indexed.putIfAbsent(decision.suggestionId(), decision) != null) {
                throw new IllegalArgumentException("同一训练建议不能重复应用");
            }
        }
        return indexed;
    }

    private List<TrainingPlanGenerator.AdjustmentConstraint> createConstraints(
            InterviewReport report,
            List<InterviewReport.Suggestion> suggestions
    ) {
        List<TrainingPlanGenerator.AdjustmentConstraint> constraints =
                new ArrayList<>(suggestions.size());

        for (InterviewReport.Suggestion suggestion : suggestions) {
            if (suggestion.type()
                    != InterviewReport.SuggestionType.TRAINING_PLAN_ADJUSTMENT) {
                throw new IllegalArgumentException("Memory建议不能通过训练计划端口处理");
            }
            if (!(suggestion.payload()
                    instanceof InterviewReport.TrainingPlanAdjustmentPayload payload)) {
                if (suggestion.payload() instanceof InterviewReport.LegacyPayload) {
                    throw new IllegalStateException("旧版字符串训练建议不可执行，请重新生成报告");
                }
                throw new IllegalStateException("训练计划建议payload类型不合法");
            }

            constraints.add(new TrainingPlanGenerator.AdjustmentConstraint(
                    suggestion.suggestionId(),
                    report.reportId(),
                    payload.focusArea(),
                    payload.adjustment(),
                    suggestion.contentHash()
            ));
        }
        return List.copyOf(constraints);
    }

    private UUID stablePlanId(
            InterviewReport report,
            List<TrainingPlanGenerator.AdjustmentConstraint> constraints
    ) {
        StringBuilder identity = new StringBuilder(ID_NAMESPACE)
                .append('\0')
                .append(report.ownerId().value())
                .append('\0')
                .append(report.reportId());

        constraints.stream()
                .map(TrainingPlanGenerator.AdjustmentConstraint::suggestionId)
                .sorted()
                .forEach(suggestionId -> identity.append('\0').append(suggestionId));

        return UUID.nameUUIDFromBytes(
                identity.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    private void requireAppliedOutputs(
            List<InterviewReportConfirmation.Decision> decisions,
            UUID planId
    ) {
        boolean conflict = decisions.stream()
                .filter(decision -> decision.applicationStatus()
                        == InterviewReportConfirmation.ApplicationStatus.APPLIED)
                .map(InterviewReportConfirmation.Decision::outputReferenceId)
                .anyMatch(existingPlanId -> !planId.equals(existingPlanId));
        if (conflict) {
            throw new IllegalStateException("已应用训练建议引用了不同的训练计划");
        }
    }

    private TrainingPlan requireReplay(
            TrainingPlan plan,
            InterviewReport report,
            UUID gapSnapshotId,
            UUID planId
    ) {
        if (!plan.planId().equals(planId)
                || !plan.ownerId().equals(report.ownerId())
                || !plan.gapSnapshotId().equals(gapSnapshotId)
                || plan.generationContext() == null
                || plan.status() == TrainingPlan.PlanStatus.DRAFT) {
            throw new IllegalStateException("稳定planId已被不同训练计划占用");
        }
        return plan;
    }
}