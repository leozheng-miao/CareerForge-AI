package com.leo.careerforgeai.interviewflow.application.context;

import com.leo.careerforgeai.interviewflow.application.context.InterviewModelContext.ActiveTopicContext;
import com.leo.careerforgeai.interviewflow.application.context.InterviewModelContext.ClosedTopicSummary;
import com.leo.careerforgeai.interviewflow.application.context.InterviewModelContext.TurnContext;
import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository.AnswerRecord;
import com.leo.careerforgeai.interviewflow.application.port.InterviewInputSnapshotRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository.QuestionDetails;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTopicRepository;
import com.leo.careerforgeai.interviewflow.application.session.InterviewFlowNotFoundException;
import com.leo.careerforgeai.interviewflow.domain.topic.InterviewTopic;
import com.leo.careerforgeai.interviewflow.domain.topic.TopicStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 按任务目的从面试事实组装活动主题全文和已关闭主题摘要
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Component
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class InterviewContextAssembler {

    private static final int SEGMENT_MAX_CHARS = 2_000;

    private final InterviewSessionRepository sessionRepository;
    private final InterviewInputSnapshotRepository inputRepository;
    private final InterviewTopicRepository topicRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;

    public InterviewContextAssembler(
            InterviewSessionRepository sessionRepository,
            InterviewInputSnapshotRepository inputRepository,
            InterviewTopicRepository topicRepository,
            InterviewQuestionRepository questionRepository,
            InterviewAnswerRepository answerRepository
    ) {
        this.sessionRepository = Objects.requireNonNull(
                sessionRepository,
                "sessionRepository不能为空"
        );
        this.inputRepository = Objects.requireNonNull(
                inputRepository,
                "inputRepository不能为空"
        );
        this.topicRepository = Objects.requireNonNull(
                topicRepository,
                "topicRepository不能为空"
        );
        this.questionRepository = Objects.requireNonNull(
                questionRepository,
                "questionRepository不能为空"
        );
        this.answerRepository = Objects.requireNonNull(
                answerRepository,
                "answerRepository不能为空"
        );
    }

    @Transactional(readOnly = true)
    public InterviewModelContext assemble(
            ContextPurpose purpose,
            InterviewContextKey key
    ) {
        Objects.requireNonNull(purpose, "purpose不能为空");
        Objects.requireNonNull(key, "key不能为空");
        var aggregate = sessionRepository.findById(
                        key.ownerId(),
                        key.interviewId()
                )
                .orElseThrow(() -> new InterviewFlowNotFoundException(
                        key.interviewId()
                ));
        var versions = sessionRepository.findVersions(
                        key.ownerId(),
                        key.interviewId()
                )
                .orElseThrow(() -> new InterviewFlowNotFoundException(
                        key.interviewId()
                ));
        var input = inputRepository.findBySession(
                        key.ownerId(),
                        key.interviewId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "面试缺少输入快照"
                ));
        if (!aggregate.session().ownerId().equals(key.ownerId())) {
            throw new InterviewFlowNotFoundException(key.interviewId());
        }

        AnswerRecord currentAnswer = answerRepository.findById(
                        key.ownerId(),
                        key.interviewId(),
                        key.currentAnswerId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "面试上下文缺少当前答案"
                ));
        QuestionDetails currentQuestion = questionRepository
                .findQuestionDetails(
                        key.ownerId(),
                        key.interviewId(),
                        currentAnswer.answer().questionId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "面试上下文缺少当前问题"
                ));
        List<InterviewTopic> topics = topicRepository.findTopics(
                key.ownerId(),
                key.interviewId()
        );
        InterviewTopic active = topics.stream()
                .filter(topic -> topic.topicId().equals(
                        currentQuestion.question().topicId()
                ))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "面试上下文缺少当前考查目标"
                ));

        List<TurnContext> activeTurns = questionRepository.findQuestions(
                        key.ownerId(),
                        key.interviewId()
                ).stream()
                .filter(question -> question.topicId().equals(
                        active.topicId()
                ))
                .map(question -> turnContext(key, question.questionId()))
                .toList();
        List<ClosedTopicSummary> closed = topics.stream()
                .filter(topic -> topic.status() == TopicStatus.COMPLETED
                        || topic.status() == TopicStatus.SKIPPED)
                .map(topic -> closedSummary(key, topic))
                .toList();

        return new InterviewModelContext(
                purpose,
                key.interviewId(),
                key.ownerId(),
                input.direction(),
                versions.inputVersion(),
                versions.planVersion(),
                new ActiveTopicContext(
                        active.topicId(),
                        active.sequenceNo(),
                        active.objective(),
                        activeTurns
                ),
                closed
        );
    }

    private TurnContext turnContext(
            InterviewContextKey key,
            UUID questionId
    ) {
        QuestionDetails details = questionRepository.findQuestionDetails(
                        key.ownerId(),
                        key.interviewId(),
                        questionId
                )
                .orElseThrow();
        AnswerRecord answer = answerRepository.findByQuestion(
                key.ownerId(),
                key.interviewId(),
                questionId
        ).orElse(null);
        String content = answer == null
                ? ""
                : Objects.requireNonNullElse(
                        answer.answer().submittedContent(),
                        ""
                );
        return new TurnContext(
                questionId,
                details.question().content(),
                semanticSegments(content),
                answer == null ? null : answer.answerContentHash(),
                details.sourceRefsJson(),
                answer == null ? null : answer.turnAnalysisJson()
        );
    }

    private ClosedTopicSummary closedSummary(
            InterviewContextKey key,
            InterviewTopic topic
    ) {
        List<AnswerRecord> answers = questionRepository.findQuestions(
                        key.ownerId(),
                        key.interviewId()
                ).stream()
                .filter(question -> question.topicId().equals(topic.topicId()))
                .map(question -> answerRepository.findByQuestion(
                        key.ownerId(),
                        key.interviewId(),
                        question.questionId()
                ).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        String summary = answers.stream()
                .map(AnswerRecord::turnAnalysisJson)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("{\"status\":\"NO_ANALYSIS\"}");
        return new ClosedTopicSummary(
                topic.topicId(),
                topic.sequenceNo(),
                summary,
                answers.stream()
                        .map(answer -> answer.answer().answerId())
                        .toList()
        );
    }

    static List<String> semanticSegments(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> segments = new ArrayList<>();
        String[] paragraphs = content.strip().split("(?<=[。！？.!?])\\s*|\\R+");
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.isBlank()) {
                continue;
            }
            if (paragraph.length() > SEGMENT_MAX_CHARS) {
                flush(current, segments);
                for (int start = 0; start < paragraph.length();
                        start += SEGMENT_MAX_CHARS) {
                    segments.add(paragraph.substring(
                            start,
                            Math.min(start + SEGMENT_MAX_CHARS,
                                    paragraph.length())
                    ));
                }
                continue;
            }
            if (!current.isEmpty()
                    && current.length() + paragraph.length() + 1
                    > SEGMENT_MAX_CHARS) {
                flush(current, segments);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(paragraph);
        }
        flush(current, segments);
        return List.copyOf(segments);
    }

    private static void flush(
            StringBuilder current,
            List<String> segments
    ) {
        if (!current.isEmpty()) {
            segments.add(current.toString());
            current.setLength(0);
        }
    }

    public record InterviewContextKey(
            ActorId ownerId,
            UUID interviewId,
            UUID currentAnswerId
    ) {

        public InterviewContextKey {
            Objects.requireNonNull(ownerId, "ownerId不能为空");
            Objects.requireNonNull(interviewId, "interviewId不能为空");
            Objects.requireNonNull(currentAnswerId, "currentAnswerId不能为空");
        }
    }
}
