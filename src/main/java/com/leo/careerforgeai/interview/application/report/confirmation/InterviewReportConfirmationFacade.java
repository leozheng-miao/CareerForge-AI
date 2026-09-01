package com.leo.careerforgeai.interview.application.report.confirmation;

import com.leo.careerforgeai.interview.domain.report.InterviewReportConfirmation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 组合报告确认提交短事务和下游建议应用流程
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
@Service
@ConditionalOnBean({
        InterviewReportConfirmationSubmissionService.class,
        InterviewReportConfirmationApplicationService.class
})
public class InterviewReportConfirmationFacade {

    private final InterviewReportConfirmationSubmissionService submissionService;
    private final InterviewReportConfirmationApplicationService applicationService;

    public InterviewReportConfirmationFacade(
            InterviewReportConfirmationSubmissionService submissionService,
            InterviewReportConfirmationApplicationService applicationService
    ) {
        this.submissionService = Objects.requireNonNull(submissionService, "submissionService不能为空");
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService不能为空");
    }

    public InterviewReportConfirmation confirm(
            UUID interviewId,
            UUID reportId,
            UUID requestId,
            long expectedReportVersion,
            List<InterviewReportConfirmationFactory.Selection> selections
    ) {
        submissionService.submit(
                interviewId,
                reportId,
                requestId,
                expectedReportVersion,
                selections
        );
        return applicationService.apply(interviewId, reportId);
    }
}