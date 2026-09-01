package com.leo.careerforgeai.interview.application.port;

import com.leo.careerforgeai.interview.domain.report.InterviewReport;
import com.leo.careerforgeai.interview.domain.report.InterviewReportConfirmation;

import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义已确认报告Memory建议写入长期记忆候选区的跨模块边界
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
public interface InterviewMemorySuggestionApplicationPort {

    UUID apply(
            InterviewReport report,
            InterviewReportConfirmation.Decision decision
    );
}