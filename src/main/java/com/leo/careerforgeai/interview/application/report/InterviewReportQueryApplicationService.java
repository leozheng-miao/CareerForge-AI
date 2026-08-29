package com.leo.careerforgeai.interview.application.report;

import com.leo.careerforgeai.interview.application.port.InterviewReportConfirmationRepository;
import com.leo.careerforgeai.interview.application.port.InterviewReportRepository;
import com.leo.careerforgeai.interview.domain.InterviewReport;
import com.leo.careerforgeai.interview.domain.InterviewReportConfirmation;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 按当前用户边界查询面试报告和报告确认结果
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
@Service
@ConditionalOnBean({
        InterviewReportRepository.class,
        InterviewReportConfirmationRepository.class
})
public class InterviewReportQueryApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final InterviewReportRepository reportRepository;
    private final InterviewReportConfirmationRepository confirmationRepository;

    public InterviewReportQueryApplicationService(
            CurrentActorProvider currentActorProvider,
            InterviewReportRepository reportRepository,
            InterviewReportConfirmationRepository confirmationRepository
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.reportRepository = Objects.requireNonNull(reportRepository, "reportRepository不能为空");
        this.confirmationRepository = Objects.requireNonNull(confirmationRepository, "confirmationRepository不能为空");
    }

    @Transactional(readOnly = true)
    public InterviewReport getReport(UUID interviewId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        return reportRepository.findByInterview(currentActor(), interviewId)
                .orElseThrow(() -> new InterviewReportConfirmationException(
                        InterviewReportConfirmationException.Reason.REPORT_NOT_FOUND,
                        "面试报告不存在"
                ));
    }

    @Transactional(readOnly = true)
    public InterviewReportConfirmation getConfirmation(
            UUID interviewId,
            UUID reportId
    ) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(reportId, "reportId不能为空");
        return confirmationRepository.findByReport(currentActor(), interviewId, reportId)
                .orElseThrow(() -> new InterviewReportConfirmationException(
                        InterviewReportConfirmationException.Reason.CONFIRMATION_NOT_FOUND,
                        "报告确认单不存在"
                ));
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }
}