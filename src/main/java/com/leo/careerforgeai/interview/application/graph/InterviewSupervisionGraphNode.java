package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.interview.application.supervision.InterviewSupervisionApplicationService;
import com.leo.careerforgeai.interview.application.supervision.InterviewSupervisorDecision;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 从Graph State读取事实引用并调用只读Java Supervisor生成路由元数据
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
@Component
@ConditionalOnBean(InterviewSupervisionApplicationService.class)
public class InterviewSupervisionGraphNode {

    private final InterviewSupervisionApplicationService supervisionService;

    public InterviewSupervisionGraphNode(InterviewSupervisionApplicationService supervisionService) {
        this.supervisionService = Objects.requireNonNull(supervisionService, "supervisionService不能为空");
    }

    public Map<String, Object> superviseRound(InterviewGraphState state) {
        Objects.requireNonNull(state, "state不能为空");
        if (state.currentRound() < 1) throw new IllegalStateException("Checkpoint尚未进入有效回合");

        state.reviewPlan().orElseThrow(() -> new IllegalStateException("Checkpoint缺少reviewPlan"));
        UUID questionId = state.currentQuestionId()
                .orElseThrow(() -> new IllegalStateException("Checkpoint缺少currentQuestionId"));
        UUID answerId = state.answerId()
                .orElseThrow(() -> new IllegalStateException("Checkpoint缺少answerId"));
        UUID technicalReviewId = state.technicalReviewId()
                .orElseThrow(() -> new IllegalStateException("Checkpoint缺少technicalReviewId"));
        UUID evidenceReviewId = state.evidenceReviewId()
                .orElseThrow(() -> new IllegalStateException("Checkpoint缺少evidenceReviewId"));

        InterviewSupervisorDecision decision = supervisionService.superviseRound(
                state.interviewId(),
                state.currentRound(),
                questionId,
                answerId,
                technicalReviewId,
                evidenceReviewId
        );
        return InterviewGraphState.supervisionDecisionUpdate(
                decision.routeDecision(),
                decision.failureCode()
        );
    }
}