package com.leo.careerforgeai.interview.application.report.confirmation;

import com.leo.careerforgeai.interview.application.port.InterviewMemorySuggestionApplicationPort;
import com.leo.careerforgeai.interview.application.port.InterviewReportConfirmationRepository;
import com.leo.careerforgeai.interview.application.port.InterviewReportRepository;
import com.leo.careerforgeai.interview.application.port.InterviewTrainingPlanSuggestionApplicationPort;
import com.leo.careerforgeai.interview.application.port.MockInterviewInputSnapshotRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.domain.report.InterviewReport;
import com.leo.careerforgeai.interview.domain.report.InterviewReportConfirmation;
import com.leo.careerforgeai.interview.domain.session.InterviewStatus;
import com.leo.careerforgeai.interview.domain.session.MockInterviewInputSnapshot;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 幂等应用报告逐项确认结果并最终完成报告和模拟面试
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
@Slf4j
@Service
@ConditionalOnBean({
        InterviewReportRepository.class,
        InterviewReportConfirmationRepository.class,
        MockInterviewSessionRepository.class,
        MockInterviewInputSnapshotRepository.class,
        InterviewMemorySuggestionApplicationPort.class,
        InterviewTrainingPlanSuggestionApplicationPort.class
})
public class InterviewReportConfirmationApplicationService {

    private static final int MAX_CAS_ATTEMPTS = 3;
    private static final String MEMORY_APPLICATION_FAILED = "MEMORY_APPLICATION_FAILED";
    private static final String TRAINING_PLAN_APPLICATION_FAILED = "TRAINING_PLAN_APPLICATION_FAILED";
    private static final String SUGGESTION_APPLICATION_FAILED = "SUGGESTION_APPLICATION_FAILED";

    private final CurrentActorProvider currentActorProvider;
    private final InterviewReportRepository reportRepository;
    private final InterviewReportConfirmationRepository confirmationRepository;
    private final MockInterviewSessionRepository sessionRepository;
    private final MockInterviewInputSnapshotRepository inputSnapshotRepository;
    private final InterviewMemorySuggestionApplicationPort memoryApplicationPort;
    private final InterviewTrainingPlanSuggestionApplicationPort trainingPlanApplicationPort;
    private final Clock clock;

    public InterviewReportConfirmationApplicationService(
            CurrentActorProvider currentActorProvider,
            InterviewReportRepository reportRepository,
            InterviewReportConfirmationRepository confirmationRepository,
            MockInterviewSessionRepository sessionRepository,
            MockInterviewInputSnapshotRepository inputSnapshotRepository,
            InterviewMemorySuggestionApplicationPort memoryApplicationPort,
            InterviewTrainingPlanSuggestionApplicationPort trainingPlanApplicationPort,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.reportRepository = Objects.requireNonNull(reportRepository, "reportRepository不能为空");
        this.confirmationRepository = Objects.requireNonNull(confirmationRepository, "confirmationRepository不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.inputSnapshotRepository = Objects.requireNonNull(inputSnapshotRepository, "inputSnapshotRepository不能为空");
        this.memoryApplicationPort = Objects.requireNonNull(memoryApplicationPort, "memoryApplicationPort不能为空");
        this.trainingPlanApplicationPort = Objects.requireNonNull(
                trainingPlanApplicationPort, "trainingPlanApplicationPort不能为空"
        );
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    public InterviewReportConfirmation apply(UUID interviewId, UUID reportId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(reportId, "reportId不能为空");

        ActorId ownerId = currentActor();
        MockInterviewSession session = sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));
        InterviewReport report = reportRepository.findById(ownerId, interviewId, reportId)
                .orElseThrow(() -> new InterviewReportConfirmationException(
                        InterviewReportConfirmationException.Reason.REPORT_NOT_FOUND,
                        "面试报告不存在"
                ));
        InterviewReportConfirmation confirmation = loadConfirmation(ownerId, interviewId, reportId);

        requireScope(session, report, confirmation);

        if (confirmation.status() == InterviewReportConfirmation.Status.PENDING_APPLICATION) {
            confirmation = applyMemoryDecisions(report, confirmation);

            if (hasPendingTrainingDecision(report, confirmation)) {
                MockInterviewInputSnapshot inputSnapshot = inputSnapshotRepository
                        .findById(ownerId, session.inputSnapshotId())
                        .orElseThrow(() -> conflict("面试输入快照不存在"));
                requireInputSnapshot(session, inputSnapshot);
                confirmation = applyTrainingPlanDecisions(report, inputSnapshot, confirmation);
            }

            confirmation = finishConfirmation(confirmation);
        }

        finalizeReport(report, confirmation);
        finalizeSession(session, confirmation);
        return confirmation;
    }

    private InterviewReportConfirmation applyMemoryDecisions(
            InterviewReport report,
            InterviewReportConfirmation confirmation
    ) {
        List<UUID> decisionIds = confirmation.decisions().stream()
                .filter(this::isPendingConfirmedDecision)
                .filter(decision -> suggestion(report, decision).type()
                        == InterviewReport.SuggestionType.MEMORY_CANDIDATE)
                .map(InterviewReportConfirmation.Decision::decisionId)
                .toList();

        InterviewReportConfirmation current = confirmation;
        for (UUID decisionId : decisionIds) {
            InterviewReportConfirmation.Decision decision = decision(current, decisionId);
            if (!isPendingConfirmedDecision(decision)) continue;

            try {
                UUID memoryId = memoryApplicationPort.apply(report, decision);
                current = markApplied(current, decisionId, memoryId);
            } catch (RuntimeException exception) {
                log.warn(
                        "面试报告Memory建议应用失败，reportId={}, decisionId={}, exception={}",
                        report.reportId(), decisionId, exception.getClass().getSimpleName()
                );
                current = markFailed(current, decisionId, MEMORY_APPLICATION_FAILED);
            }
        }
        return current;
    }

    private InterviewReportConfirmation applyTrainingPlanDecisions(
            InterviewReport report,
            MockInterviewInputSnapshot inputSnapshot,
            InterviewReportConfirmation confirmation
    ) {
        List<InterviewReportConfirmation.Decision> trainingDecisions = confirmation.decisions().stream()
                .filter(decision -> decision.decisionType()
                        == InterviewReportConfirmation.DecisionType.CONFIRMED)
                .filter(decision -> suggestion(report, decision).type()
                        == InterviewReport.SuggestionType.TRAINING_PLAN_ADJUSTMENT)
                .toList();

        if (trainingDecisions.stream().noneMatch(this::isPendingConfirmedDecision)) {
            return confirmation;
        }

        try {
            UUID planId = trainingPlanApplicationPort.apply(report, inputSnapshot, trainingDecisions);
            requireExistingTrainingOutput(trainingDecisions, planId);

            InterviewReportConfirmation current = confirmation;
            for (InterviewReportConfirmation.Decision decision : trainingDecisions) {
                if (decision(current, decision.decisionId()).applicationStatus()
                        == InterviewReportConfirmation.ApplicationStatus.PENDING) {
                    current = markApplied(current, decision.decisionId(), planId);
                }
            }
            return current;
        } catch (RuntimeException exception) {
            log.warn(
                    "面试报告训练计划建议应用失败，reportId={}, exception={}",
                    report.reportId(), exception.getClass().getSimpleName()
            );

            InterviewReportConfirmation current = confirmation;
            for (InterviewReportConfirmation.Decision decision : trainingDecisions) {
                if (decision(current, decision.decisionId()).applicationStatus()
                        == InterviewReportConfirmation.ApplicationStatus.PENDING) {
                    current = markFailed(
                            current, decision.decisionId(), TRAINING_PLAN_APPLICATION_FAILED
                    );
                }
            }
            return current;
        }
    }

    private InterviewReportConfirmation markApplied(
            InterviewReportConfirmation confirmation,
            UUID decisionId,
            UUID outputReferenceId
    ) {
        InterviewReportConfirmation current = confirmation;

        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            InterviewReportConfirmation.Decision storedDecision = decision(current, decisionId);

            if (storedDecision.applicationStatus()
                    == InterviewReportConfirmation.ApplicationStatus.APPLIED) {
                if (!outputReferenceId.equals(storedDecision.outputReferenceId())) {
                    throw conflict("同一确认决定产生了不同的下游引用");
                }
                return current;
            }
            if (storedDecision.applicationStatus()
                    != InterviewReportConfirmation.ApplicationStatus.PENDING) {
                return current;
            }

            Instant now = operationTime(current.updatedAt(), storedDecision.updatedAt());
            InterviewReportConfirmation candidate = current.recordDecision(
                    storedDecision.applied(outputReferenceId, now)
            );
            if (confirmationRepository.updateIfVersionMatches(
                    current.ownerId(), candidate, current.version()
            )) {
                return candidate;
            }
            current = loadConfirmation(
                    current.ownerId(), current.interviewId(), current.reportId()
            );
        }
        throw conflict("确认决定应用结果发生并发冲突");
    }

    private InterviewReportConfirmation markFailed(
            InterviewReportConfirmation confirmation,
            UUID decisionId,
            String failureCode
    ) {
        InterviewReportConfirmation current = confirmation;

        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            InterviewReportConfirmation.Decision storedDecision = decision(current, decisionId);
            if (storedDecision.applicationStatus()
                    != InterviewReportConfirmation.ApplicationStatus.PENDING) {
                return current;
            }

            Instant now = operationTime(current.updatedAt(), storedDecision.updatedAt());
            InterviewReportConfirmation candidate = current.recordDecision(
                    storedDecision.failed(failureCode, now)
            );
            if (confirmationRepository.updateIfVersionMatches(
                    current.ownerId(), candidate, current.version()
            )) {
                return candidate;
            }
            current = loadConfirmation(
                    current.ownerId(), current.interviewId(), current.reportId()
            );
        }
        throw conflict("确认决定失败结果发生并发冲突");
    }

    private InterviewReportConfirmation finishConfirmation(
            InterviewReportConfirmation confirmation
    ) {
        InterviewReportConfirmation current = confirmation;

        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            if (current.status() != InterviewReportConfirmation.Status.PENDING_APPLICATION) {
                return current;
            }

            boolean hasFailure = current.decisions().stream().anyMatch(
                    decision -> decision.applicationStatus()
                            == InterviewReportConfirmation.ApplicationStatus.FAILED
            );
            InterviewReportConfirmation candidate = current.finish(
                    hasFailure ? SUGGESTION_APPLICATION_FAILED : null,
                    operationTime(current.updatedAt())
            );
            if (confirmationRepository.updateIfVersionMatches(
                    current.ownerId(), candidate, current.version()
            )) {
                return candidate;
            }
            current = loadConfirmation(
                    current.ownerId(), current.interviewId(), current.reportId()
            );
        }
        throw conflict("确认单完成状态发生并发冲突");
    }

    private void finalizeReport(
            InterviewReport report,
            InterviewReportConfirmation confirmation
    ) {
        if (confirmation.status() == InterviewReportConfirmation.Status.PENDING_APPLICATION) {
            throw conflict("确认单尚未结束，不能完成报告");
        }
        if (report.status() == InterviewReport.Status.DECIDED) return;

        InterviewReport updated = report.decide(operationTime(report.updatedAt()));
        if (reportRepository.updateIfVersionMatches(
                report.ownerId(), updated, report.version()
        )) {
            return;
        }

        InterviewReport stored = reportRepository
                .findById(report.ownerId(), report.interviewId(), report.reportId())
                .orElseThrow(() -> conflict("完成报告后无法重新读取"));
        if (stored.status() != InterviewReport.Status.DECIDED) {
            throw conflict("报告完成状态发生并发冲突");
        }
    }

    private void finalizeSession(
            MockInterviewSession session,
            InterviewReportConfirmation confirmation
    ) {
        if (confirmation.status() == InterviewReportConfirmation.Status.PENDING_APPLICATION) {
            throw conflict("确认单尚未结束，不能完成面试");
        }
        if (session.status() == InterviewStatus.COMPLETED) return;
        if (session.status() != InterviewStatus.AWAITING_CONFIRMATION) {
            throw conflict("当前面试不在等待确认状态");
        }

        MockInterviewSession updated = session.complete(operationTime(session.updatedAt()));
        if (sessionRepository.updateIfVersionMatches(
                session.ownerId(), updated, session.version()
        )) {
            return;
        }

        MockInterviewSession stored = sessionRepository
                .findById(session.ownerId(), session.interviewId())
                .orElseThrow(() -> conflict("完成面试后无法重新读取"));
        if (stored.status() != InterviewStatus.COMPLETED) {
            throw conflict("面试完成状态发生并发冲突");
        }
    }

    private void requireScope(
            MockInterviewSession session,
            InterviewReport report,
            InterviewReportConfirmation confirmation
    ) {
        if (!session.ownerId().equals(report.ownerId())
                || !session.ownerId().equals(confirmation.ownerId())
                || !session.interviewId().equals(report.interviewId())
                || !session.interviewId().equals(confirmation.interviewId())
                || !report.reportId().equals(confirmation.reportId())) {
            throw conflict("面试、报告和确认单作用域不一致");
        }
        if (report.status() == InterviewReport.Status.PENDING_CONFIRMATION
                && report.version() != confirmation.expectedReportVersion()) {
            throw new InterviewReportConfirmationException(
                    InterviewReportConfirmationException.Reason.REPORT_VERSION_CONFLICT,
                    "报告版本已经变化"
            );
        }
        if (session.status() != InterviewStatus.AWAITING_CONFIRMATION
                && session.status() != InterviewStatus.COMPLETED) {
            throw conflict("当前面试不允许应用报告确认结果");
        }
    }

    private void requireInputSnapshot(
            MockInterviewSession session,
            MockInterviewInputSnapshot inputSnapshot
    ) {
        if (!inputSnapshot.ownerId().equals(session.ownerId())
                || !inputSnapshot.inputSnapshotId().equals(session.inputSnapshotId())
                || !inputSnapshot.snapshotHash().equals(session.inputSnapshotHash())) {
            throw conflict("面试输入快照与会话冻结输入不一致");
        }
    }

    private void requireExistingTrainingOutput(
            List<InterviewReportConfirmation.Decision> decisions,
            UUID planId
    ) {
        Objects.requireNonNull(planId, "planId不能为空");
        boolean conflictingOutput = decisions.stream()
                .filter(decision -> decision.applicationStatus()
                        == InterviewReportConfirmation.ApplicationStatus.APPLIED)
                .map(InterviewReportConfirmation.Decision::outputReferenceId)
                .anyMatch(existingPlanId -> !planId.equals(existingPlanId));
        if (conflictingOutput) {
            throw conflict("训练计划建议重放产生了不同的计划引用");
        }
    }

    private boolean hasPendingTrainingDecision(
            InterviewReport report,
            InterviewReportConfirmation confirmation
    ) {
        return confirmation.decisions().stream()
                .filter(this::isPendingConfirmedDecision)
                .anyMatch(decision -> suggestion(report, decision).type()
                        == InterviewReport.SuggestionType.TRAINING_PLAN_ADJUSTMENT);
    }

    private boolean isPendingConfirmedDecision(
            InterviewReportConfirmation.Decision decision
    ) {
        return decision.decisionType() == InterviewReportConfirmation.DecisionType.CONFIRMED
                && decision.applicationStatus()
                == InterviewReportConfirmation.ApplicationStatus.PENDING;
    }

    private InterviewReport.Suggestion suggestion(
            InterviewReport report,
            InterviewReportConfirmation.Decision decision
    ) {
        return report.suggestions().stream()
                .filter(candidate -> candidate.suggestionId().equals(decision.suggestionId()))
                .findFirst()
                .orElseThrow(() -> conflict("确认决定引用的报告建议不存在"));
    }

    private InterviewReportConfirmation.Decision decision(
            InterviewReportConfirmation confirmation,
            UUID decisionId
    ) {
        return confirmation.decisions().stream()
                .filter(candidate -> candidate.decisionId().equals(decisionId))
                .findFirst()
                .orElseThrow(() -> conflict("确认决定不存在"));
    }

    private InterviewReportConfirmation loadConfirmation(
            ActorId ownerId,
            UUID interviewId,
            UUID reportId
    ) {
        return confirmationRepository.findByReport(ownerId, interviewId, reportId)
                .orElseThrow(() -> new InterviewReportConfirmationException(
                        InterviewReportConfirmationException.Reason.CONFIRMATION_NOT_FOUND,
                        "报告确认单不存在"
                ));
    }

    private Instant operationTime(Instant... lowerBounds) {
        Instant now = clock.instant();
        for (Instant lowerBound : lowerBounds) {
            if (lowerBound != null && now.isBefore(lowerBound)) now = lowerBound;
        }
        return now;
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }

    private InterviewReportConfirmationException conflict(String message) {
        return new InterviewReportConfirmationException(
                InterviewReportConfirmationException.Reason.APPLICATION_CONFLICT,
                message
        );
    }
}