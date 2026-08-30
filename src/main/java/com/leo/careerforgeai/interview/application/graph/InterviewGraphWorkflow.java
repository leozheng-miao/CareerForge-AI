package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.interview.domain.InterviewRouteDecision;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * @program: CareerForge-AI
 * @description: 定义多轮提问、HITL、并行评审、Java Supervisor和报告生成主图
 * @author: Miao Zheng
 * @date: 2026-08-29
 */
@Component
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
@ConditionalOnBean({
        InterviewGraphNodes.class,
        InterviewReviewGraphNodes.class,
        InterviewSupervisionGraphNode.class,
        InterviewRouteGraphNodes.class,
        InterviewReportGraphNode.class
})
public class InterviewGraphWorkflow {

    public static final String GRAPH_ID = "careerforge-interview-v1";
    public static final String LOAD_FROZEN_CONTEXT = "load_frozen_context";
    public static final String GENERATE_AND_PERSIST_QUESTION = "generate_and_persist_question";
    public static final String VALIDATE_ANSWER_RESUME = "validate_answer_resume";
    public static final String PREPARE_REVIEWS = "prepare_reviews";
    public static final String TECHNICAL_REVIEW = "technical_review";
    public static final String EVIDENCE_REVIEW = "evidence_review";
    public static final String JOIN_REVIEWS = "join_reviews";
    public static final String SUPERVISE_ROUND = "supervise_round";
    public static final String CONTINUE_QUESTIONING = "continue_questioning";
    public static final String START_REPORT_GENERATION = "start_report_generation";
    public static final String GENERATE_AND_PERSIST_REPORT = "generate_and_persist_report";
    public static final String FINALIZE_FAILURE = "finalize_failure";

    private final InterviewGraphNodes nodes;
    private final InterviewReviewGraphNodes reviewNodes;
    private final InterviewSupervisionGraphNode supervisionNode;
    private final InterviewRouteGraphNodes routeNodes;
    private final InterviewReportGraphNode reportNode;

    public InterviewGraphWorkflow(
            InterviewGraphNodes nodes,
            InterviewReviewGraphNodes reviewNodes,
            InterviewSupervisionGraphNode supervisionNode,
            InterviewRouteGraphNodes routeNodes,
            InterviewReportGraphNode reportNode
    ) {
        this.nodes = Objects.requireNonNull(nodes, "nodes不能为空");
        this.reviewNodes = Objects.requireNonNull(reviewNodes, "reviewNodes不能为空");
        this.supervisionNode = Objects.requireNonNull(supervisionNode, "supervisionNode不能为空");
        this.routeNodes = Objects.requireNonNull(routeNodes, "routeNodes不能为空");
        this.reportNode = Objects.requireNonNull(reportNode, "reportNode不能为空");
    }

    public CompiledGraph<InterviewGraphState> compile(BaseCheckpointSaver checkpointSaver) throws GraphStateException {
        Objects.requireNonNull(checkpointSaver, "checkpointSaver不能为空");
        StateGraph<InterviewGraphState> graph = new StateGraph<>(InterviewGraphState::new);

        graph.addNode(LOAD_FROZEN_CONTEXT, node_async(nodes::loadFrozenContext));
        graph.addNode(GENERATE_AND_PERSIST_QUESTION, node_async(nodes::generateAndPersistQuestion));
        graph.addNode(VALIDATE_ANSWER_RESUME, node_async(nodes::validateAnswerResume));
        graph.addNode(PREPARE_REVIEWS, node_async(reviewNodes::prepareReviews));
        graph.addNode(TECHNICAL_REVIEW, node_async(reviewNodes::technicalReview));
        graph.addNode(EVIDENCE_REVIEW, node_async(reviewNodes::evidenceReview));
        graph.addNode(JOIN_REVIEWS, node_async(reviewNodes::joinReviews));
        graph.addNode(SUPERVISE_ROUND, node_async(supervisionNode::superviseRound));
        graph.addNode(CONTINUE_QUESTIONING, node_async(routeNodes::continueQuestioning));
        graph.addNode(START_REPORT_GENERATION, node_async(routeNodes::startReportGeneration));
        graph.addNode(GENERATE_AND_PERSIST_REPORT, node_async(reportNode::generateAndPersistReport));
        graph.addNode(FINALIZE_FAILURE, node_async(routeNodes::finalizeFailure));

        graph.addEdge(START, LOAD_FROZEN_CONTEXT);
        graph.addEdge(LOAD_FROZEN_CONTEXT, GENERATE_AND_PERSIST_QUESTION);
        graph.addEdge(GENERATE_AND_PERSIST_QUESTION, VALIDATE_ANSWER_RESUME);
        graph.addEdge(VALIDATE_ANSWER_RESUME, PREPARE_REVIEWS);
        graph.addEdge(PREPARE_REVIEWS, TECHNICAL_REVIEW);
        graph.addEdge(PREPARE_REVIEWS, EVIDENCE_REVIEW);
        graph.addEdge(TECHNICAL_REVIEW, JOIN_REVIEWS);
        graph.addEdge(EVIDENCE_REVIEW, JOIN_REVIEWS);
        graph.addEdge(JOIN_REVIEWS, SUPERVISE_ROUND);

        graph.addConditionalEdges(
                SUPERVISE_ROUND,
                edge_async(InterviewGraphWorkflow::routeAfterSupervision),
                Map.of(
                        InterviewRouteDecision.FOLLOW_UP.name(), CONTINUE_QUESTIONING,
                        InterviewRouteDecision.NEXT_QUESTION.name(), CONTINUE_QUESTIONING,
                        InterviewRouteDecision.GENERATE_REPORT.name(), START_REPORT_GENERATION,
                        InterviewRouteDecision.FINALIZE_FAILURE.name(), FINALIZE_FAILURE
                )
        );

        graph.addEdge(CONTINUE_QUESTIONING, GENERATE_AND_PERSIST_QUESTION);
        graph.addEdge(START_REPORT_GENERATION, GENERATE_AND_PERSIST_REPORT);
        graph.addEdge(GENERATE_AND_PERSIST_REPORT, END);
        graph.addEdge(FINALIZE_FAILURE, END);

        return graph.compile(CompileConfig.builder()
                .graphId(GRAPH_ID)
                .checkpointSaver(checkpointSaver)
                .interruptAfter(GENERATE_AND_PERSIST_QUESTION, GENERATE_AND_PERSIST_REPORT)
                .releaseThread(false)
                .build());
    }

    private static String routeAfterSupervision(InterviewGraphState state) {
        return state.routeDecision()
                .orElseThrow(() -> new IllegalStateException("Supervisor没有返回routeDecision"))
                .name();
    }
}