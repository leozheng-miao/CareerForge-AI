package com.leo.careerforgeai.interviewflow.application.query;

import com.leo.careerforgeai.interviewflow.application.port.*;
import com.leo.careerforgeai.interviewflow.application.report.InterviewReportNotFoundException;
import com.leo.careerforgeai.interviewflow.application.session.InterviewFlowNotFoundException;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.turn.InterviewQuestion;
import com.leo.careerforgeai.interviewflow.domain.turn.QuestionStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class InterviewQueryService {
    private final CurrentActorProvider actors;
    private final InterviewSessionRepository sessions;
    private final InterviewQuestionRepository questions;
    private final InterviewAnswerRepository answers;
    private final InterviewReportRepository reports;

    public InterviewQueryService(CurrentActorProvider actors,
                                 InterviewSessionRepository sessions,
                                 InterviewQuestionRepository questions,
                                 InterviewAnswerRepository answers,
                                 InterviewReportRepository reports) {
        this.actors = Objects.requireNonNull(actors);
        this.sessions = Objects.requireNonNull(sessions);
        this.questions = Objects.requireNonNull(questions);
        this.answers = Objects.requireNonNull(answers);
        this.reports = Objects.requireNonNull(reports);
    }

    @Transactional(readOnly = true)
    public InterviewAggregate get(UUID interviewId) {
        ActorId owner = actors.currentActor();
        return sessions.findById(owner, interviewId)
                .orElseThrow(() -> new InterviewFlowNotFoundException(interviewId));
    }

    @Transactional(readOnly = true)
    public List<InterviewAggregate> list(Instant before, UUID beforeId, int limit) {
        return sessions.findPage(actors.currentActor(), before, beforeId, limit);
    }

    @Transactional(readOnly = true)
    public CurrentQuestion currentQuestion(UUID interviewId) {
        InterviewAggregate aggregate = get(interviewId);
        ActorId owner = aggregate.session().ownerId();
        InterviewQuestion question = questions.findQuestions(owner, interviewId).stream()
                .filter(item -> item.status() == QuestionStatus.PRESENTED)
                .findFirst().orElseThrow(() -> new IllegalStateException("当前没有可作答问题"));
        var answer = answers.findByQuestion(owner, interviewId, question.questionId());
        return new CurrentQuestion(question, answer.map(item -> item.answer().draftContent()).orElse(""),
                answer.map(InterviewAnswerRepository.AnswerRecord::version).orElse(-1L),
                aggregate.session().version());
    }

    @Transactional(readOnly = true)
    public List<HistoryTurn> history(UUID interviewId) {
        InterviewAggregate aggregate = get(interviewId);
        ActorId owner = aggregate.session().ownerId();
        return questions.findQuestions(owner, interviewId).stream()
                .map(question -> new HistoryTurn(question,
                        answers.findByQuestion(owner, interviewId, question.questionId())
                                .map(InterviewAnswerRepository.AnswerRecord::answer).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public InterviewReportRepository.ReportSnapshot report(UUID interviewId) {
        InterviewAggregate aggregate = get(interviewId);
        return reports.findByInterview(aggregate.session().ownerId(), interviewId)
                .orElseThrow(() -> new InterviewReportNotFoundException(interviewId));
    }

    public record CurrentQuestion(InterviewQuestion question, String draft,
                                  long answerVersion, long sessionVersion) {}
    public record HistoryTurn(InterviewQuestion question,
                              com.leo.careerforgeai.interviewflow.domain.turn.InterviewAnswer answer) {}
}
