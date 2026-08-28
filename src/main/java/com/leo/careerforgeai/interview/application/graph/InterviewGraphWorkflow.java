package com.leo.careerforgeai.interview.application.graph;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * @program: CareerForge-AI
 * @description: 定义模拟面试首题HITL和回答后的并行评审主图
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
@Component
@ConditionalOnBean({InterviewGraphNodes.class, InterviewReviewGraphNodes.class})
public class InterviewGraphWorkflow {

    public static final String GRAPH_ID = "careerforge-interview-v1";
    public static final String LOAD_FROZEN_CONTEXT = "load_frozen_context";
    public static final String GENERATE_AND_PERSIST_QUESTION = "generate_and_persist_question";
    public static final String VALIDATE_ANSWER_RESUME = "validate_answer_resume";
    public static final String PREPARE_REVIEWS = "prepare_reviews";
    public static final String TECHNICAL_REVIEW = "technical_review";
    public static final String EVIDENCE_REVIEW = "evidence_review";
    public static final String JOIN_REVIEWS = "join_reviews";

    private final InterviewGraphNodes nodes;
    private final InterviewReviewGraphNodes reviewNodes;

    public InterviewGraphWorkflow(InterviewGraphNodes nodes, InterviewReviewGraphNodes reviewNodes) {
        this.nodes = Objects.requireNonNull(nodes, "nodes不能为空");
        this.reviewNodes = Objects.requireNonNull(reviewNodes, "reviewNodes不能为空");
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

        graph.addEdge(START, LOAD_FROZEN_CONTEXT);
        graph.addEdge(LOAD_FROZEN_CONTEXT, GENERATE_AND_PERSIST_QUESTION);
        graph.addEdge(GENERATE_AND_PERSIST_QUESTION, VALIDATE_ANSWER_RESUME);
        graph.addEdge(VALIDATE_ANSWER_RESUME, PREPARE_REVIEWS);
        graph.addEdge(PREPARE_REVIEWS, TECHNICAL_REVIEW);
        graph.addEdge(PREPARE_REVIEWS, EVIDENCE_REVIEW);
        graph.addEdge(TECHNICAL_REVIEW, JOIN_REVIEWS);
        graph.addEdge(EVIDENCE_REVIEW, JOIN_REVIEWS);
        graph.addEdge(JOIN_REVIEWS, END);

        return graph.compile(CompileConfig.builder()
                .graphId(GRAPH_ID)
                .checkpointSaver(checkpointSaver)
                .interruptAfter(GENERATE_AND_PERSIST_QUESTION)
                .releaseThread(false)
                .build());
    }
}