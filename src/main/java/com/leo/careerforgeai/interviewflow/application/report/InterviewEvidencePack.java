package com.leo.careerforgeai.interviewflow.application.report;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;

/** 仅由已冻结回答和持久化分析组成的报告证据包。 */
public record InterviewEvidencePack(
        UUID interviewId,
        List<TopicEvidence> topics,
        InterviewMode mode
) {
    public InterviewEvidencePack(UUID interviewId, List<TopicEvidence> topics) {
        this(interviewId, topics, InterviewMode.QUICK);
    }

    public InterviewEvidencePack {
        Objects.requireNonNull(interviewId, "interviewId不能为null");
        Objects.requireNonNull(topics, "topics不能为null");
        topics = List.copyOf(topics);
        Objects.requireNonNull(mode, "mode不能为null");
    }

    public int submittedAnswerCount() {
        return topics.stream().mapToInt(topic -> topic.turns().size()).sum();
    }

    public record TopicEvidence(
            UUID topicId,
            int sequenceNo,
            String objective,
            boolean covered,
            List<TurnEvidence> turns
    ) {
        public TopicEvidence {
            Objects.requireNonNull(topicId, "topicId不能为null");
            if (sequenceNo < 1 || objective == null || objective.isBlank()) {
                throw new IllegalArgumentException("主题证据不合法");
            }
            Objects.requireNonNull(turns, "turns不能为null");
            turns = List.copyOf(turns);
        }
    }

    public record TurnEvidence(
            UUID answerId,
            UUID questionId,
            String question,
            String frozenAnswer,
            String answerContentHash,
            String analysisJson
    ) {
        public TurnEvidence {
            Objects.requireNonNull(answerId, "answerId不能为null");
            Objects.requireNonNull(questionId, "questionId不能为null");
            if (question == null || question.isBlank()
                    || frozenAnswer == null || frozenAnswer.isBlank()) {
                throw new IllegalArgumentException("回合证据必须包含题目和冻结回答");
            }
        }
    }
}
