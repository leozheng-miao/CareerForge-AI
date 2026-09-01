package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.interview.application.supervision.InterviewRouteApplicationService;
import com.leo.careerforgeai.interview.domain.session.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.session.InterviewMode;
import com.leo.careerforgeai.interview.domain.execution.InterviewRouteDecision;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @program: CareerForge-AI
 * @description: 验证Graph路由节点只执行匹配的MySQL状态迁移并清理上一轮临时状态
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
class InterviewRouteGraphNodesTest {

    private static final UUID INTERVIEW_ID = UUID.randomUUID();

    @Test
    void shouldApplyNextQuestionAndClearCompletedRoundState() {
        InterviewRouteApplicationService service = mock(InterviewRouteApplicationService.class);
        InterviewRouteGraphNodes nodes = new InterviewRouteGraphNodes(service);
        InterviewGraphState state = state(InterviewRouteDecision.NEXT_QUESTION, null);

        Map<String, Object> update = nodes.continueQuestioning(state);

        verify(service).apply(INTERVIEW_ID, 1, InterviewRouteDecision.NEXT_QUESTION, null);
        assertThat(update)
                .containsEntry(InterviewGraphState.ANSWER_ID, AgentState.MARK_FOR_REMOVAL)
                .containsEntry(InterviewGraphState.REVIEW_PLAN, AgentState.MARK_FOR_REMOVAL)
                .containsEntry(InterviewGraphState.TECHNICAL_REVIEW_ID, AgentState.MARK_FOR_REMOVAL)
                .containsEntry(InterviewGraphState.EVIDENCE_REVIEW_ID, AgentState.MARK_FOR_REMOVAL)
                .doesNotContainKey(InterviewGraphState.ROUTE_DECISION);
    }

    @Test
    void shouldApplyReportAndFailureRoutes() {
        InterviewRouteApplicationService service = mock(InterviewRouteApplicationService.class);
        InterviewRouteGraphNodes nodes = new InterviewRouteGraphNodes(service);

        nodes.startReportGeneration(state(InterviewRouteDecision.GENERATE_REPORT, null));
        nodes.finalizeFailure(state(
                InterviewRouteDecision.FINALIZE_FAILURE,
                InterviewFailureCode.BUDGET_EXHAUSTED
        ));

        verify(service).apply(INTERVIEW_ID, 1, InterviewRouteDecision.GENERATE_REPORT, null);
        verify(service).apply(
                INTERVIEW_ID,
                1,
                InterviewRouteDecision.FINALIZE_FAILURE,
                InterviewFailureCode.BUDGET_EXHAUSTED
        );
    }

    private InterviewGraphState state(
            InterviewRouteDecision routeDecision,
            InterviewFailureCode failureCode
    ) {
        Map<String, Object> data = new HashMap<>(
                InterviewGraphState.initialData(
                        INTERVIEW_ID,
                        InterviewMode.TARGETED_MOCK,
                        "a".repeat(64)
                )
        );
        data.put(InterviewGraphState.CURRENT_ROUND, 1);
        data.put(InterviewGraphState.CURRENT_QUESTION_ID, UUID.randomUUID().toString());
        data.put(InterviewGraphState.ANSWER_ID, UUID.randomUUID().toString());
        data.put(InterviewGraphState.ROUTE_DECISION, routeDecision.name());
        if (failureCode != null) {
            data.put(InterviewGraphState.LAST_ERROR_CODE, failureCode.name());
        }
        return new InterviewGraphState(data);
    }
}