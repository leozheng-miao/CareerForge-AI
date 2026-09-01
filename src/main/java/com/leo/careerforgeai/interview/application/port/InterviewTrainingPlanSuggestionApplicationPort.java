package com.leo.careerforgeai.interview.application.port;

import com.leo.careerforgeai.interview.domain.report.InterviewReport;
import com.leo.careerforgeai.interview.domain.report.InterviewReportConfirmation;
import com.leo.careerforgeai.interview.domain.session.MockInterviewInputSnapshot;

import java.util.List;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义已确认训练建议生成新PENDING_CONFIRMATION训练计划的跨模块边界
 * @author: Miao Zheng
 * @date: 2026-08-30
 */
public interface InterviewTrainingPlanSuggestionApplicationPort {

    UUID apply(
            InterviewReport report,
            MockInterviewInputSnapshot inputSnapshot,
            List<InterviewReportConfirmation.Decision> decisions
    );
}