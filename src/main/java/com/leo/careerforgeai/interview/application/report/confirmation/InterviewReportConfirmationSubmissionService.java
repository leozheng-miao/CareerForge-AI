package com.leo.careerforgeai.interview.application.report.confirmation;

import com.leo.careerforgeai.interview.application.port.InterviewReportConfirmationRepository;
import com.leo.careerforgeai.interview.application.port.InterviewReportRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.domain.report.InterviewReport;
import com.leo.careerforgeai.interview.domain.report.InterviewReportConfirmation;
import com.leo.careerforgeai.interview.domain.session.InterviewStatus;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 校验报告确认请求并按owner和requestId幂等认领确认单
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Service
@ConditionalOnBean({
        InterviewReportRepository.class,
        InterviewReportConfirmationRepository.class,
        MockInterviewSessionRepository.class
})
public class InterviewReportConfirmationSubmissionService {

    private final CurrentActorProvider currentActorProvider;
    private final InterviewReportRepository reportRepository;
    private final InterviewReportConfirmationRepository confirmationRepository;
    private final MockInterviewSessionRepository sessionRepository;
    private final InterviewReportConfirmationFactory confirmationFactory;
    private final Clock clock;

    public InterviewReportConfirmationSubmissionService(
            CurrentActorProvider currentActorProvider,
            InterviewReportRepository reportRepository,
            InterviewReportConfirmationRepository confirmationRepository,
            MockInterviewSessionRepository sessionRepository,
            InterviewReportConfirmationFactory confirmationFactory,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.reportRepository = Objects.requireNonNull(reportRepository, "reportRepository不能为空");
        this.confirmationRepository = Objects.requireNonNull(confirmationRepository, "confirmationRepository不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.confirmationFactory = Objects.requireNonNull(confirmationFactory, "confirmationFactory不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public InterviewReportConfirmation submit(
            UUID interviewId,
            UUID reportId,
            UUID requestId,
            long expectedReportVersion,
            List<InterviewReportConfirmationFactory.Selection> selections
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(reportId, "reportId不能为空");
        Objects.requireNonNull(requestId, "requestId不能为空");

        ActorId ownerId = currentActor();
        String fingerprint = confirmationFactory.fingerprint(
                interviewId, reportId, expectedReportVersion, selections
        );

        Optional<InterviewReportConfirmation> replay =
                confirmationRepository.findByRequest(ownerId, requestId);
        if (replay.isPresent()) {
            return requireReplay(replay.get(), interviewId, reportId, requestId, fingerprint);
        }

        MockInterviewSession session = sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));
        InterviewReport report = reportRepository.findById(ownerId, interviewId, reportId)
                .orElseThrow(() -> new InterviewReportConfirmationException(
                        InterviewReportConfirmationException.Reason.REPORT_NOT_FOUND,
                        "面试报告不存在"
                ));

        requireConfirmable(session, report, expectedReportVersion);
        InterviewReportConfirmation candidate = confirmationFactory.create(
                report, requestId, expectedReportVersion, selections, clock.instant()
        );
        InterviewReportConfirmation stored = confirmationRepository.claim(candidate);
        return requireClaim(candidate, stored);
    }

    private InterviewReportConfirmation requireReplay(
            InterviewReportConfirmation stored,
            UUID interviewId,
            UUID reportId,
            UUID requestId,
            String fingerprint
    ) {
        if (!stored.interviewId().equals(interviewId)
                || !stored.reportId().equals(reportId)
                || !stored.requestId().equals(requestId)
                || !stored.requestFingerprint().equals(fingerprint)) {
            throw new InterviewReportConfirmationException(
                    InterviewReportConfirmationException.Reason.REQUEST_CONFLICT,
                    "requestId已被用于不同的报告确认请求"
            );
        }
        return stored;
    }

    private void requireConfirmable(
            MockInterviewSession session,
            InterviewReport report,
            long expectedReportVersion
    ) {
        if (!session.ownerId().equals(report.ownerId())
                || !session.interviewId().equals(report.interviewId())) {
            throw new InterviewReportConfirmationException(
                    InterviewReportConfirmationException.Reason.REPORT_STATE_CONFLICT,
                    "报告与面试作用域不一致"
            );
        }
        if (session.status() != InterviewStatus.AWAITING_CONFIRMATION
                || report.status() != InterviewReport.Status.PENDING_CONFIRMATION) {
            throw new InterviewReportConfirmationException(
                    InterviewReportConfirmationException.Reason.REPORT_STATE_CONFLICT,
                    "当前报告不在可确认状态"
            );
        }
        if (report.version() != expectedReportVersion) {
            throw new InterviewReportConfirmationException(
                    InterviewReportConfirmationException.Reason.REPORT_VERSION_CONFLICT,
                    "报告版本已经变化，请刷新后重新确认"
            );
        }
    }

    private InterviewReportConfirmation requireClaim(
            InterviewReportConfirmation candidate,
            InterviewReportConfirmation stored
    ) {
        if (!stored.confirmationId().equals(candidate.confirmationId())
                || !stored.ownerId().equals(candidate.ownerId())
                || !stored.interviewId().equals(candidate.interviewId())
                || !stored.reportId().equals(candidate.reportId())
                || !stored.requestId().equals(candidate.requestId())
                || !stored.requestFingerprint().equals(candidate.requestFingerprint())) {
            throw new InterviewReportConfirmationException(
                    InterviewReportConfirmationException.Reason.REQUEST_CONFLICT,
                    "报告已经由另一个确认请求处理"
            );
        }
        return stored;
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }
}