package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.interview.application.report.InterviewReportGenerationService;
import com.leo.careerforgeai.interview.domain.report.InterviewReport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 调用报告应用服务并向Graph State写入待确认报告ID
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Component
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
@ConditionalOnBean(InterviewReportGenerationService.class)
public class InterviewReportGraphNode {

    private final InterviewReportGenerationService generationService;
    private final Duration modelCallTimeout;

    public InterviewReportGraphNode(
            InterviewReportGenerationService generationService,
            @Value("${careerforge.agent.loop.model-call-timeout}") Duration modelCallTimeout
    ) {
        this.generationService = Objects.requireNonNull(generationService, "generationService不能为空");
        if (modelCallTimeout == null || modelCallTimeout.isZero() || modelCallTimeout.isNegative()) {
            throw new IllegalArgumentException("modelCallTimeout必须大于0");
        }
        this.modelCallTimeout = modelCallTimeout;
    }

    public Map<String, Object> generateAndPersistReport(InterviewGraphState state) {
        Objects.requireNonNull(state, "state不能为空");
        InterviewReport report = generationService.generateAndPersist(
                state.interviewId(), modelCallTimeout
        );
        if (!report.interviewId().equals(state.interviewId())) {
            throw new IllegalStateException("生成的报告不属于当前面试");
        }
        return InterviewGraphState.waitingForReportConfirmationUpdate(report.reportId());
    }
}