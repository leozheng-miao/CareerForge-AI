package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.interview.application.supervision.InterviewRouteApplicationService;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.InterviewRouteDecision;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 校验Graph条件路由并通过应用服务落地下一题、报告或失败状态
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
@Component
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
@ConditionalOnBean(InterviewRouteApplicationService.class)
public class InterviewRouteGraphNodes {

    private final InterviewRouteApplicationService routeService;

    public InterviewRouteGraphNodes(InterviewRouteApplicationService routeService) {
        this.routeService = Objects.requireNonNull(routeService, "routeService不能为空");
    }

    public Map<String, Object> continueQuestioning(InterviewGraphState state) {
        Objects.requireNonNull(state, "state不能为空");
        InterviewRouteDecision routeDecision = requireRoute(state);
        if (routeDecision != InterviewRouteDecision.FOLLOW_UP
                && routeDecision != InterviewRouteDecision.NEXT_QUESTION) {
            throw new IllegalStateException("continue_questioning只接受FOLLOW_UP或NEXT_QUESTION");
        }
        routeService.apply(state.interviewId(), requireRound(state), routeDecision, null);
        return InterviewGraphState.clearCompletedRoundForNextQuestionUpdate();
    }

    public Map<String, Object> startReportGeneration(InterviewGraphState state) {
        Objects.requireNonNull(state, "state不能为空");
        InterviewRouteDecision routeDecision = requireRoute(state);
        if (routeDecision != InterviewRouteDecision.GENERATE_REPORT) {
            throw new IllegalStateException("start_report_generation只接受GENERATE_REPORT");
        }
        routeService.apply(state.interviewId(), requireRound(state), routeDecision, null);
        return InterviewGraphState.clearCompletedRoundUpdate();
    }

    public Map<String, Object> finalizeFailure(InterviewGraphState state) {
        Objects.requireNonNull(state, "state不能为空");
        InterviewRouteDecision routeDecision = requireRoute(state);
        if (routeDecision != InterviewRouteDecision.FINALIZE_FAILURE) {
            throw new IllegalStateException("finalize_failure只接受FINALIZE_FAILURE");
        }
        InterviewFailureCode failureCode = state.lastErrorCode()
                .orElseThrow(() -> new IllegalStateException("FINALIZE_FAILURE缺少lastErrorCode"));
        routeService.apply(state.interviewId(), requireRound(state), routeDecision, failureCode);
        return Map.of();
    }

    private static InterviewRouteDecision requireRoute(InterviewGraphState state) {
        return state.routeDecision()
                .orElseThrow(() -> new IllegalStateException("Checkpoint缺少routeDecision"));
    }

    private static int requireRound(InterviewGraphState state) {
        if (state.currentRound() < 1) throw new IllegalStateException("Checkpoint尚未进入有效回合");
        return state.currentRound();
    }
}