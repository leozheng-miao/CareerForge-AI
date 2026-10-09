package com.leo.careerforgeai.interviewflow.application.context;

import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 从MySQL事实重建的受限模型上下文，不依赖供应商会话记忆
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
public record InterviewModelContext(
        ContextPurpose purpose,
        UUID interviewId,
        ActorId ownerId,
        String direction,
        long inputVersion,
        long planVersion,
        ActiveTopicContext activeTopic,
        List<ClosedTopicSummary> closedTopics
) {

    public InterviewModelContext {
        Objects.requireNonNull(purpose, "purpose不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        if (direction == null || direction.isBlank()) {
            throw new IllegalArgumentException("direction不能为空");
        }
        if (inputVersion < 1 || planVersion < 1) {
            throw new IllegalArgumentException("上下文版本不合法");
        }
        Objects.requireNonNull(activeTopic, "activeTopic不能为空");
        Objects.requireNonNull(closedTopics, "closedTopics不能为空");
        closedTopics = List.copyOf(closedTopics);
    }

    public record ActiveTopicContext(
            UUID topicId,
            int sequenceNo,
            String objective,
            List<TurnContext> turns
    ) {

        public ActiveTopicContext {
            Objects.requireNonNull(topicId, "topicId不能为空");
            if (sequenceNo < 1 || objective == null || objective.isBlank()) {
                throw new IllegalArgumentException("活动主题不合法");
            }
            Objects.requireNonNull(turns, "turns不能为空");
            turns = List.copyOf(turns);
        }
    }

    public record TurnContext(
            UUID questionId,
            String question,
            List<String> answerSegments,
            String answerContentHash,
            String sourceRefsJson,
            String turnAnalysisJson
    ) {

        public TurnContext {
            Objects.requireNonNull(questionId, "questionId不能为空");
            if (question == null || question.isBlank()) {
                throw new IllegalArgumentException("question不能为空");
            }
            Objects.requireNonNull(answerSegments, "answerSegments不能为空");
            answerSegments = List.copyOf(answerSegments);
            if (sourceRefsJson == null || sourceRefsJson.isBlank()) {
                throw new IllegalArgumentException("sourceRefsJson不能为空");
            }
        }
    }

    public record ClosedTopicSummary(
            UUID topicId,
            int sequenceNo,
            String summaryJson,
            List<UUID> answerIds
    ) {

        public ClosedTopicSummary {
            Objects.requireNonNull(topicId, "topicId不能为空");
            if (sequenceNo < 1 || summaryJson == null
                    || summaryJson.isBlank()) {
                throw new IllegalArgumentException("关闭主题摘要不合法");
            }
            Objects.requireNonNull(answerIds, "answerIds不能为空");
            answerIds = List.copyOf(answerIds);
        }
    }
}
