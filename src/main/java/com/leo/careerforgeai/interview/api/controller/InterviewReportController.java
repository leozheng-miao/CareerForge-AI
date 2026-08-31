package com.leo.careerforgeai.interview.api.controller;

import com.leo.careerforgeai.interview.api.dto.report.ConfirmInterviewReportRequest;
import com.leo.careerforgeai.interview.api.dto.report.InterviewReportConfirmationResponse;
import com.leo.careerforgeai.interview.api.dto.report.InterviewReportResponse;
import com.leo.careerforgeai.interview.application.report.InterviewReportConfirmationFacade;
import com.leo.careerforgeai.interview.application.report.InterviewReportConfirmationFactory;
import com.leo.careerforgeai.interview.application.report.InterviewReportQueryApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 提供当前用户面试报告查询、逐项确认和确认结果查询API
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
@RestController
@RequestMapping("/api/mock-interviews")
@Tag(name = "Mock Interview Report")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class InterviewReportController {

    private final InterviewReportQueryApplicationService queryService;
    private final InterviewReportConfirmationFacade confirmationFacade;

    public InterviewReportController(
            InterviewReportQueryApplicationService queryService,
            InterviewReportConfirmationFacade confirmationFacade
    ) {
        this.queryService = Objects.requireNonNull(queryService, "queryService不能为空");
        this.confirmationFacade = Objects.requireNonNull(confirmationFacade, "confirmationFacade不能为空");
    }

    @GetMapping("/{interviewId}/report")
    @Operation(summary = "查询当前用户指定模拟面试的复盘报告")
    public BaseResponse<InterviewReportResponse> getReport(
            @PathVariable UUID interviewId
    ) {
        return ResultUtils.success(
                InterviewReportResponse.from(queryService.getReport(interviewId))
        );
    }

    @PostMapping("/{interviewId}/reports/{reportId}/confirmation")
    @Operation(summary = "逐项确认或拒绝报告建议并应用确认结果")
    public BaseResponse<InterviewReportConfirmationResponse> confirm(
            @PathVariable UUID interviewId,
            @PathVariable UUID reportId,
            @Valid @RequestBody ConfirmInterviewReportRequest request
    ) {
        return ResultUtils.success(InterviewReportConfirmationResponse.from(
                confirmationFacade.confirm(
                        interviewId,
                        reportId,
                        request.requestId(),
                        request.expectedVersion(),
                        request.decisions().stream()
                                .map(decision -> new InterviewReportConfirmationFactory.Selection(
                                        decision.suggestionId(),
                                        decision.decisionType()
                                ))
                                .toList()
                )
        ));
    }

    @GetMapping("/{interviewId}/reports/{reportId}/confirmation")
    @Operation(summary = "查询当前用户指定报告的确认和下游应用结果")
    public BaseResponse<InterviewReportConfirmationResponse> getConfirmation(
            @PathVariable UUID interviewId,
            @PathVariable UUID reportId
    ) {
        return ResultUtils.success(InterviewReportConfirmationResponse.from(
                queryService.getConfirmation(interviewId, reportId)
        ));
    }
}