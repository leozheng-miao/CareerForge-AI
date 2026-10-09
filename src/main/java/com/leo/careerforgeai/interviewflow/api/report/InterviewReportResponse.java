package com.leo.careerforgeai.interviewflow.api.report;

import com.leo.careerforgeai.interviewflow.application.port.InterviewReportRepository.ReportSnapshot;
import com.leo.careerforgeai.interviewflow.application.port.InterviewReportRepository.ReportStatus;
import java.time.Instant;
import java.util.UUID;

public record InterviewReportResponse(UUID reportId, UUID interviewId,
        ReportStatus status, String reportJson, String failureCode,
        long version, Instant updatedAt) {
    public static InterviewReportResponse from(ReportSnapshot value) {
        return new InterviewReportResponse(value.reportId(), value.interviewId(),
                value.status(), value.reportJson(), value.failureCode(),
                value.version(), value.updatedAt());
    }
}
