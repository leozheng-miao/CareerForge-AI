package com.leo.careerforgeai.agent.evaluation.compatibility;

import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * @program: CareerForge-AI
 * @description: 验证LangGraph4j 1.8.24并行fork-join及Jackson 2/3稳定State边界。
 * @author: Miao Zheng
 * @date: 2026-08-27
 */
class LangGraph4jStateAndParallelCompatibilityTest {

    @Test
    void shouldRunIndependentBranchesOnDedicatedVirtualThreadsAndJoin() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new);

        graph.addNode("prepare_reviews", node_async(state -> Map.of("prepared", true)));
        graph.addNode("technical_review", node_async(state ->
                executeBranch(barrier, "technicalReview", "technical-ok")));
        graph.addNode("evidence_review", node_async(state ->
                executeBranch(barrier, "evidenceReview", "evidence-ok")));
        graph.addNode("join_reviews", node_async(state -> Map.of(
                "joined",
                state.<String>value("technicalReview").orElseThrow()
                        + "|"
                        + state.<String>value("evidenceReview").orElseThrow()
        )));

        graph.addEdge(START, "prepare_reviews");
        graph.addEdge("prepare_reviews", "technical_review");
        graph.addEdge("prepare_reviews", "evidence_review");
        graph.addEdge("technical_review", "join_reviews");
        graph.addEdge("evidence_review", "join_reviews");
        graph.addEdge("join_reviews", END);

        var workflow = graph.compile();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            RunnableConfig config = RunnableConfig.builder()
                    .addParallelNodeExecutor("prepare_reviews", executor)
                    .build();

            AgentState completed = workflow.invoke(
                    GraphInput.args(Map.of("schemaVersion", 1)),
                    config
            ).orElseThrow();

            assertThat(completed.<String>value("technicalReview"))
                    .contains("technical-ok");
            assertThat(completed.<String>value("evidenceReview"))
                    .contains("evidence-ok");
            assertThat(completed.<String>value("joined"))
                    .contains("technical-ok|evidence-ok");
            assertThat(completed.<Boolean>value("technicalReviewVirtual"))
                    .contains(true);
            assertThat(completed.<Boolean>value("evidenceReviewVirtual"))
                    .contains(true);
        }
    }

    @Test
    void shouldRoundTripStableStateAcrossJackson2AndJackson3() throws Exception {
        Map<String, Object> original = Map.of(
                "schemaVersion", 1,
                "runId", "cp0-run-001",
                "waiting", true,
                "questionNumber", 2,
                "skills", List.of("JAVA", "SPRING_AI"),
                "review", Map.of(
                        "reviewer", "technical",
                        "score", 4,
                        "passed", true
                )
        );

        com.fasterxml.jackson.databind.ObjectMapper jackson2 =
                new com.fasterxml.jackson.databind.ObjectMapper();
        JsonMapper jackson3 = JsonMapper.builder().build();

        String jackson2Json = jackson2.writeValueAsString(original);
        Map<String, Object> decodedByJackson3 = jackson3.readValue(
                jackson2Json,
                new TypeReference<>() {
                }
        );

        String jackson3Json = jackson3.writeValueAsString(original);
        Map<String, Object> decodedByJackson2 = jackson2.readValue(
                jackson3Json,
                new com.fasterxml.jackson.core.type.TypeReference<>() {
                }
        );

        assertThat(decodedByJackson3).isEqualTo(original);
        assertThat(decodedByJackson2).isEqualTo(original);
    }

    private Map<String, Object> executeBranch(
            CyclicBarrier barrier,
            String stateKey,
            String result
    ) throws Exception {
        barrier.await(2, TimeUnit.SECONDS);
        return Map.of(
                stateKey, result,
                stateKey + "Virtual", Thread.currentThread().isVirtual()
        );
    }
}