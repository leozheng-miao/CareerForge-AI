package com.leo.careerforgeai.interviewflow.api.report;

import com.leo.careerforgeai.interviewflow.application.query.InterviewQueryService;
import com.leo.careerforgeai.interviewflow.application.report.InterviewReportApplicationService;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.UUID;

@RestController
@RequestMapping("/api/interviews/{interviewId}")
@Tag(name = "Interview Flow Report")
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class InterviewReportController {
    private final InterviewQueryService queries;
    private final InterviewReportApplicationService reports;
    public InterviewReportController(InterviewQueryService queries,
                                     InterviewReportApplicationService reports) {
        this.queries = queries; this.reports = reports;
    }
    @GetMapping("/report")
    @Operation(summary = "读取报告生成状态或已校验正式报告")
    public BaseResponse<InterviewReportResponse> get(@PathVariable UUID interviewId) {
        return ResultUtils.success(InterviewReportResponse.from(queries.report(interviewId)));
    }
    @PostMapping("/retry")
    @Operation(summary = "仅重试失败的报告任务")
    public BaseResponse<InterviewReportResponse> retry(@PathVariable UUID interviewId) {
        return ResultUtils.success(InterviewReportResponse.from(reports.retry(interviewId)));
    }
}
