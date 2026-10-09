package com.leo.careerforgeai.interviewflow.application.report;

import com.leo.careerforgeai.interviewflow.application.port.*;
import com.leo.careerforgeai.interviewflow.application.port.InterviewReportRepository.ReportSnapshot;
import com.leo.careerforgeai.interviewflow.application.port.InterviewReportRepository.ReportStatus;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskDefinition;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskType;
import com.leo.careerforgeai.interviewflow.application.session.InterviewFlowNotFoundException;
import com.leo.careerforgeai.interviewflow.application.report.InterviewEvidencePack.TopicEvidence;
import com.leo.careerforgeai.interviewflow.application.report.InterviewEvidencePack.TurnEvidence;
import com.leo.careerforgeai.interviewflow.domain.session.*;
import com.leo.careerforgeai.interviewflow.domain.turn.AnswerStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.stereotype.Service;
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
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class InterviewReportApplicationService {
    private final CurrentActorProvider actors;
    private final InterviewSessionRepository sessions;
    private final InterviewTopicRepository topics;
    private final InterviewQuestionRepository questions;
    private final InterviewAnswerRepository answers;
    private final InterviewReportRepository reports;
    private final InterviewTaskRepository tasks;
    private final InterviewReportEngine engine;
    private final InterviewEventPublisher events;
    private final JsonMapper json;
    private final Clock clock;

    public InterviewReportApplicationService(CurrentActorProvider actors,
            InterviewSessionRepository sessions, InterviewTopicRepository topics,
            InterviewQuestionRepository questions, InterviewAnswerRepository answers,
            InterviewReportRepository reports, InterviewTaskRepository tasks,
            InterviewReportEngine engine, InterviewEventPublisher events,
            JsonMapper json, Clock clock) {
        this.actors = Objects.requireNonNull(actors);
        this.sessions = Objects.requireNonNull(sessions);
        this.topics = Objects.requireNonNull(topics);
        this.questions = Objects.requireNonNull(questions);
        this.answers = Objects.requireNonNull(answers);
        this.reports = Objects.requireNonNull(reports);
        this.tasks = Objects.requireNonNull(tasks);
        this.engine = Objects.requireNonNull(engine);
        this.events = Objects.requireNonNull(events);
        this.json = Objects.requireNonNull(json);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public ReportSnapshot retry(UUID interviewId) {
        ActorId owner = actors.currentActor();
        InterviewAggregate aggregate = owned(owner, interviewId);
        ReportSnapshot failed = reports.findByInterview(owner, interviewId)
                .orElseThrow(() -> new IllegalStateException("报告尚未创建"));
        if (failed.status() != ReportStatus.FAILED) {
            return failed;
        }
        Instant now = clock.instant();
        ReportSnapshot pending = new ReportSnapshot(failed.reportId(), interviewId, owner,
                ReportStatus.PENDING, null, failed.schemaVersion(), failed.inputVersion(),
                failed.planVersion(), failed.contextFingerprint(), null,
                failed.version() + 1, failed.createdAt(), now, null);
        if (!reports.updateIfVersionMatches(owner, pending, failed.version())) {
            throw new IllegalStateException("报告状态已变化");
        }
        enqueueReport(aggregate, pending.contextFingerprint(), "retry:" + pending.version(), now);
        return pending;
    }

    public void generate(ActorId owner, UUID interviewId, String contextFingerprint) {
        InterviewAggregate aggregate = owned(owner, interviewId);
        if (aggregate.session().stage() != SessionStage.GENERATING_REPORT) {
            if (aggregate.session().stage() == SessionStage.COMPLETED) return;
            throw new IllegalStateException("仅生成报告阶段可执行报告任务");
        }
        var versions = sessions.findVersions(owner, interviewId).orElseThrow();
        Instant now = clock.instant();
        ReportSnapshot current = reports.findByInterview(owner, interviewId).orElseGet(() -> {
            ReportSnapshot created = new ReportSnapshot(UUID.randomUUID(), interviewId, owner,
                    ReportStatus.PENDING, null, 1, versions.inputVersion(), versions.planVersion(),
                    contextFingerprint, null, 0, now, now, null);
            reports.insert(created);
            return created;
        });
        if (current.status() == ReportStatus.READY) {
            completeSession(owner, aggregate, current);
            return;
        }
        ReportSnapshot generating = changeStatus(current, ReportStatus.GENERATING, null, null, now);
        if (!reports.updateIfVersionMatches(owner, generating, current.version())) {
            throw new IllegalStateException("报告已被其他任务处理");
        }
        try {
            InterviewEvidencePack evidence = evidence(owner, interviewId);
            var generated = engine.generate(evidence);
            String reportJson = json.writeValueAsString(generated);
            Instant completedAt = clock.instant();
            ReportSnapshot ready = changeStatus(generating, ReportStatus.READY,
                    reportJson, null, completedAt);
            if (!reports.updateIfVersionMatches(owner, ready, generating.version())) {
                throw new IllegalStateException("报告发布竞争失败");
            }
            completeSession(owner, owned(owner, interviewId), ready);
        } catch (JacksonException exception) {
            fail(owner, generating, "REPORT_SERIALIZATION_FAILED");
            throw new IllegalStateException("报告序列化失败", exception);
        } catch (RuntimeException exception) {
            fail(owner, generating, "REPORT_GENERATION_FAILED");
            throw exception;
        }
    }

    public void failTerminalTask(
            ActorId owner,
            UUID interviewId,
            String failureCode
    ) {
        Objects.requireNonNull(owner, "owner不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("failureCode不能为空");
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            ReportSnapshot current = reports.findByInterview(owner, interviewId)
                    .orElse(null);
            if (current == null
                    || current.status() == ReportStatus.READY
                    || current.status() == ReportStatus.FAILED) {
                return;
            }
            Instant now = clock.instant();
            ReportSnapshot failed = changeStatus(
                    current,
                    ReportStatus.FAILED,
                    null,
                    failureCode,
                    now
            );
            if (reports.updateIfVersionMatches(
                    owner,
                    failed,
                    current.version()
            )) {
                return;
            }
        }
        throw new IllegalStateException("报告终态失败同步冲突");
    }

    private InterviewEvidencePack evidence(ActorId owner, UUID interviewId) {
        var allQuestions = questions.findQuestions(owner, interviewId);
        var allAnswers = answers.findAnswersByInterview(owner, interviewId);
        List<TopicEvidence> evidence = topics.findTopics(owner, interviewId).stream().map(topic -> {
            List<TurnEvidence> turns = allQuestions.stream()
                    .filter(q -> q.topicId().equals(topic.topicId()))
                    .flatMap(q -> allAnswers.stream()
                            .filter(a -> a.answer().questionId().equals(q.questionId()))
                            .filter(a -> a.answer().status() != AnswerStatus.DISCARDED)
                            .filter(a -> a.answer().submittedContent() != null)
                            .map(a -> new TurnEvidence(a.answer().answerId(), q.questionId(),
                                    q.content(), a.answer().submittedContent(),
                                    a.answerContentHash(), a.turnAnalysisJson())))
                    .toList();
            return new TopicEvidence(topic.topicId(), topic.sequenceNo(), topic.objective(),
                    !turns.isEmpty(), turns);
        }).toList();
        return new InterviewEvidencePack(interviewId, evidence);
    }

    private void fail(ActorId owner, ReportSnapshot report, String code) {
        Instant now = clock.instant();
        ReportSnapshot failed = changeStatus(report, ReportStatus.FAILED, null, code, now);
        reports.updateIfVersionMatches(owner, failed, report.version());
        safePublish(new InterviewEventPublisher.SafeInterviewEvent(
                UUID.randomUUID().toString(), report.interviewId(), owner,
                InterviewEventPublisher.EventType.RETRYABLE_FAILURE,
                report.reportId().toString(), ReportStatus.FAILED.name(), report.version() + 1, now));
    }

    private void completeSession(
            ActorId owner,
            InterviewAggregate aggregate,
            ReportSnapshot ready
    ) {
        if (aggregate.session().stage() == SessionStage.COMPLETED) {
            return;
        }
        if (aggregate.session().stage() != SessionStage.GENERATING_REPORT) {
            throw new IllegalStateException("报告已就绪但会话状态不允许完成");
        }
        Instant now = clock.instant();
        InterviewAggregate completed = new InterviewStateMachine(new QuickInterviewPolicy())
                .transition(aggregate, InterviewEvent.REPORT_GENERATED, now).aggregate();
        if (!sessions.updateIfVersionMatches(
                owner, completed, aggregate.session().version()
        )) {
            throw new IllegalStateException("报告完成时会话状态已变化");
        }
        safePublish(new InterviewEventPublisher.SafeInterviewEvent(
                UUID.randomUUID().toString(), aggregate.session().interviewId(), owner,
                InterviewEventPublisher.EventType.REPORT_READY,
                ready.reportId().toString(), ready.status().name(),
                completed.session().version(), now));
    }

    private void safePublish(InterviewEventPublisher.SafeInterviewEvent event) {
        try {
            events.publish(event);
        } catch (RuntimeException ignored) {
            // Redis/SSE是辅助通道，发布失败不改变已持久化的报告事实。
        }
    }

    private static ReportSnapshot changeStatus(ReportSnapshot current, ReportStatus status,
            String reportJson, String failureCode, Instant now) {
        return new ReportSnapshot(current.reportId(), current.interviewId(), current.ownerId(),
                status, reportJson, current.schemaVersion(), current.inputVersion(),
                current.planVersion(), current.contextFingerprint(), failureCode,
                current.version() + 1, current.createdAt(), now,
                status == ReportStatus.READY || status == ReportStatus.FAILED ? now : null);
    }

    private InterviewAggregate owned(ActorId owner, UUID interviewId) {
        return sessions.findById(owner, interviewId)
                .orElseThrow(() -> new InterviewFlowNotFoundException(interviewId));
    }

    private void enqueueReport(InterviewAggregate aggregate, String fingerprint,
                               String suffix, Instant now) {
        String key = "report:" + aggregate.session().interviewId() + ":" + suffix;
        boolean enqueued = tasks.enqueue(new TaskDefinition(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)),
                aggregate.session().interviewId(), aggregate.session().ownerId(),
                TaskType.GENERATE_REPORT, key, 3, 1, 1, fingerprint, now));
        if (!enqueued) {
            throw new IllegalStateException("面试报告重试任务入队失败");
        }
    }
}
