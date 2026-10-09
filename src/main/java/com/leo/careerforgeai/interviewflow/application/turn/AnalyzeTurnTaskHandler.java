package com.leo.careerforgeai.interviewflow.application.turn;

import com.leo.careerforgeai.interviewflow.application.context.ContextPurpose;
import com.leo.careerforgeai.interviewflow.application.context.InterviewContextAssembler;
import com.leo.careerforgeai.interviewflow.application.context.InterviewContextAssembler.InterviewContextKey;
import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository.AnswerRecord;
import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository.FinalDecision;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository.QuestionDetails;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository.QuestionRecord;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository.QuestionWrite;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.ClaimedTask;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskDefinition;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskType;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTopicRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTopicRepository.TopicRecord;
import com.leo.careerforgeai.interviewflow.application.task.InterviewTaskHandlerRegistry.InterviewTaskHandler;
import com.leo.careerforgeai.interviewflow.application.turn.TurnDecisionEngine.TurnDecision;
import com.leo.careerforgeai.interviewflow.application.turn.TurnDecisionEngine.TurnDecisionInput;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewEvent;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewStateMachine;
import com.leo.careerforgeai.interviewflow.domain.session.QuickInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.topic.InterviewTopic;
import com.leo.careerforgeai.interviewflow.domain.topic.TopicStatus;
import com.leo.careerforgeai.interviewflow.domain.turn.AnswerStatus;
import com.leo.careerforgeai.interviewflow.domain.turn.InterviewQuestion;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 原子保存回合分析、Java最终决策和下一道完整题目
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Component
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class AnalyzeTurnTaskHandler implements InterviewTaskHandler {

    private static final int TASK_MAX_ATTEMPTS = 3;
    private static final InterviewStateMachine STATE_MACHINE =
            new InterviewStateMachine(new QuickInterviewPolicy());

    private final InterviewSessionRepository sessionRepository;
    private final InterviewTopicRepository topicRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final InterviewTaskRepository taskRepository;
    private final InterviewContextAssembler contextAssembler;
    private final TurnDecisionEngine decisionEngine;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AnalyzeTurnTaskHandler(
            InterviewSessionRepository sessionRepository,
            InterviewTopicRepository topicRepository,
            InterviewQuestionRepository questionRepository,
            InterviewAnswerRepository answerRepository,
            InterviewTaskRepository taskRepository,
            InterviewContextAssembler contextAssembler,
            TurnDecisionEngine decisionEngine,
            JsonMapper jsonMapper,
            Clock clock
    ) {
        this.sessionRepository = Objects.requireNonNull(
                sessionRepository, "sessionRepository不能为空");
        this.topicRepository = Objects.requireNonNull(
                topicRepository, "topicRepository不能为空");
        this.questionRepository = Objects.requireNonNull(
                questionRepository, "questionRepository不能为空");
        this.answerRepository = Objects.requireNonNull(
                answerRepository, "answerRepository不能为空");
        this.taskRepository = Objects.requireNonNull(
                taskRepository, "taskRepository不能为空");
        this.contextAssembler = Objects.requireNonNull(
                contextAssembler, "contextAssembler不能为空");
        this.decisionEngine = Objects.requireNonNull(
                decisionEngine, "decisionEngine不能为空");
        this.jsonMapper = Objects.requireNonNull(
                jsonMapper, "jsonMapper不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Override
    public TaskType taskType() {
        return TaskType.ANALYZE_TURN;
    }

    @Override
    @Transactional
    public void handle(ClaimedTask task) {
        Objects.requireNonNull(task, "task不能为空");
        UUID answerId = parseAnswerId(task.businessKey());
        AnswerRecord answer = answerRepository.findById(
                        task.ownerId(), task.interviewId(), answerId)
                .orElseThrow(() -> new IllegalStateException(
                        "分析任务对应答案不存在"));
        if (!Objects.equals(
                answer.submitRequestFingerprint(),
                task.contextFingerprint()
        )) {
            throw new IllegalStateException("分析任务上下文指纹已过期");
        }
        InterviewAggregate aggregate = sessionRepository.findById(
                        task.ownerId(), task.interviewId())
                .orElseThrow(() -> new IllegalStateException(
                        "分析任务对应面试不存在"));
        QuestionDetails currentQuestion = questionRepository
                .findQuestionDetails(
                        task.ownerId(), task.interviewId(),
                        answer.answer().questionId())
                .orElseThrow(() -> new IllegalStateException(
                        "分析任务对应问题不存在"));
        TopicRecord currentTopic = topicRepository.findTopicRecord(
                        task.ownerId(), task.interviewId(),
                        currentQuestion.question().topicId())
                .orElseThrow(() -> new IllegalStateException(
                        "分析任务对应考查目标不存在"));
        InterviewTopic nextTopic = topicRepository.findTopics(
                        task.ownerId(), task.interviewId()).stream()
                .filter(topic -> topic.sequenceNo()
                        > currentTopic.topic().sequenceNo())
                .filter(topic -> topic.status() == TopicStatus.PLANNED)
                .findFirst()
                .orElse(null);
        var context = contextAssembler.assemble(
                ContextPurpose.TURN_DECISION,
                new InterviewContextKey(
                        task.ownerId(), task.interviewId(), answerId)
        );
        TurnDecision decision = decisionEngine.decide(
                new TurnDecisionInput(
                        context,
                        aggregate,
                        answer,
                        nextTopic != null
                )
        );
        persistDecision(
                task,
                aggregate,
                answer,
                currentQuestion,
                currentTopic,
                nextTopic,
                decision
        );
    }

    private void persistDecision(
            ClaimedTask task,
            InterviewAggregate aggregate,
            AnswerRecord answer,
            QuestionDetails currentQuestion,
            TopicRecord currentTopic,
            InterviewTopic nextTopic,
            TurnDecision decision
    ) {
        AnswerRecord analyzed = analyzedAnswer(answer, decision);
        if (!answerRepository.updateIfVersionMatches(
                task.ownerId(), task.interviewId(), analyzed, answer.version())) {
            throw new IllegalStateException("答案分析CAS冲突");
        }

        InterviewAggregate updated;
        if (decision.decision() == FinalDecision.FOLLOW_UP) {
            updated = STATE_MACHINE.transition(
                    aggregate, InterviewEvent.FOLLOW_UP_SELECTED)
                    .aggregate();
            insertFollowUp(task, currentQuestion, decision, updated);
        } else if (decision.decision() == FinalDecision.NEXT_TOPIC) {
            if (nextTopic == null) {
                updated = finish(task, aggregate, currentTopic);
            } else {
                updated = STATE_MACHINE.transition(
                        aggregate, InterviewEvent.NEXT_TOPIC_SELECTED)
                        .aggregate();
                completeTopic(task, currentTopic);
                activateNextTopicAndQuestion(task, nextTopic);
            }
        } else {
            updated = finish(task, aggregate, currentTopic);
        }
        if (!sessionRepository.updateIfVersionMatches(
                task.ownerId(), updated, aggregate.session().version())) {
            throw new IllegalStateException("回合决策会话CAS冲突");
        }
    }

    private AnswerRecord analyzedAnswer(
            AnswerRecord answer,
            TurnDecision decision
    ) {
        String analysisJson = writeJson(Map.of(
                "analysis", decision.analysis(),
                "modelCallCount", decision.modelCallCount(),
                "toolCallCount", decision.toolCallCount()
        ));
        String modelJson = decision.modelDecisionJson() == null
                ? "{}"
                : decision.modelDecisionJson();
        var analyzedDomain = answer.answer().status()
                == AnswerStatus.DISCARDED
                ? answer.answer()
                : answer.answer().close();
        return new AnswerRecord(
                analyzedDomain,
                answer.submitRequestId(),
                answer.submitRequestFingerprint(),
                answer.answerContentHash(),
                analysisJson,
                modelJson,
                decision.decision(),
                answer.submittedAt(),
                Math.addExact(answer.version(), 1)
        );
    }

    private void insertFollowUp(
            ClaimedTask task,
            QuestionDetails current,
            TurnDecision decision,
            InterviewAggregate updated
    ) {
        String content = decision.question();
        String fingerprint = sha256(normalize(content));
        UUID questionId = deterministicUuid(
                task.businessKey() + ":follow-up:"
                        + updated.followUpCount()
        );
        InterviewQuestion question = InterviewQuestion.reserved(
                questionId,
                current.question().topicId(),
                InterviewQuestion.QuestionType.FOLLOW_UP,
                Math.addExact(current.question().displayOrder(), 1),
                content
        ).present();
        questionRepository.insertAll(
                task.ownerId(),
                task.interviewId(),
                List.of(new QuestionWrite(
                        question,
                        current.question().questionId(),
                        writeJson(decision.sourceReferences()),
                        fingerprint,
                        task.planVersion()
                ))
        );
    }

    private void completeTopic(ClaimedTask task, TopicRecord currentTopic) {
        if (currentTopic.topic().status() == TopicStatus.ACTIVE
                && !topicRepository.updateIfVersionMatches(
                task.ownerId(), task.interviewId(),
                currentTopic.topic().complete(), currentTopic.version())) {
            throw new IllegalStateException("当前考查目标CAS冲突");
        }
    }

    private void activateNextTopicAndQuestion(
            ClaimedTask task,
            InterviewTopic nextTopic
    ) {
        TopicRecord nextRecord = topicRepository.findTopicRecord(
                        task.ownerId(), task.interviewId(), nextTopic.topicId())
                .orElseThrow();
        if (!topicRepository.updateIfVersionMatches(
                task.ownerId(), task.interviewId(),
                nextRecord.topic().activate(), nextRecord.version())) {
            throw new IllegalStateException("下一考查目标CAS冲突");
        }
        QuestionRecord nextQuestion = questionRepository.findQuestions(
                        task.ownerId(), task.interviewId()).stream()
                .filter(question -> question.topicId().equals(
                        nextTopic.topicId()))
                .filter(question -> question.type()
                        == InterviewQuestion.QuestionType.MAIN)
                .findFirst()
                .flatMap(question -> questionRepository.findQuestionRecord(
                        task.ownerId(), task.interviewId(),
                        question.questionId()))
                .orElseThrow(() -> new IllegalStateException(
                        "下一考查目标缺少预留主问题"));
        if (!questionRepository.updateIfVersionMatches(
                task.ownerId(), task.interviewId(),
                nextQuestion.question().present(), nextQuestion.version())) {
            throw new IllegalStateException("下一主问题CAS冲突");
        }
    }

    private InterviewAggregate finish(
            ClaimedTask task,
            InterviewAggregate aggregate,
            TopicRecord currentTopic
    ) {
        completeTopic(task, currentTopic);
        Instant now = clock.instant();
        var transition = STATE_MACHINE.transition(
                aggregate, InterviewEvent.FINISH_REQUESTED, now);
        InterviewAggregate updated = transition.aggregate();
        if (transition.shouldGenerateReport()) {
            String businessKey = "report:" + task.interviewId();
            boolean enqueued = taskRepository.enqueue(new TaskDefinition(
                    deterministicUuid(businessKey),
                    task.interviewId(),
                    task.ownerId(),
                    TaskType.GENERATE_REPORT,
                    businessKey,
                    TASK_MAX_ATTEMPTS,
                    task.inputVersion(),
                    task.planVersion(),
                    sha256("report:" + task.interviewId() + ":"
                            + updated.session().version()),
                    now
            ));
            if (!enqueued) {
                throw new IllegalStateException("面试报告任务入队失败");
            }
        }
        return updated;
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("回合决策JSON序列化失败", exception);
        }
    }

    private static UUID parseAnswerId(String businessKey) {
        if (businessKey == null || !businessKey.startsWith("analyze:")) {
            throw new IllegalArgumentException("分析任务businessKey不合法");
        }
        return UUID.fromString(businessKey.substring("analyze:".length()));
    }

    private static UUID deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalize(String content) {
        return content.strip().replaceAll("\\s+", " ")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JVM不支持SHA-256", exception);
        }
    }
}
