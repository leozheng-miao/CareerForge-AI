package com.leo.careerforgeai.interviewflow.application.answer;

import com.leo.careerforgeai.interviewflow.application.port.InterviewActivityLease;
import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository.AnswerRecord;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository.QuestionRecord;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskDefinition;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository.TaskType;
import com.leo.careerforgeai.interviewflow.application.session.InterviewFlowNotFoundException;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewEvent;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewStateMachine;
import com.leo.careerforgeai.interviewflow.domain.session.QuickInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.session.ComprehensiveInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
import com.leo.careerforgeai.interviewflow.domain.session.TransitionResult;
import com.leo.careerforgeai.interviewflow.domain.timer.TimerState;
import com.leo.careerforgeai.interviewflow.domain.turn.AnswerStatus;
import com.leo.careerforgeai.interviewflow.domain.turn.InterviewAnswer;
import com.leo.careerforgeai.interviewflow.domain.turn.InterviewQuestion;
import com.leo.careerforgeai.interviewflow.domain.turn.QuestionStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 以单个数据库事务保存草稿、冻结答案并创建后台任务
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@Slf4j
@Service
@ConditionalOnProperty(
        prefix = "careerforge.persistence",
        name = "enabled",
        havingValue = "true"
)
public class InterviewAnswerApplicationService {

    private static final InterviewStateMachine STATE_MACHINE =
            new InterviewStateMachine(new QuickInterviewPolicy());
    private static final int TASK_MAX_ATTEMPTS = 3;

    private final CurrentActorProvider currentActorProvider;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final InterviewTaskRepository taskRepository;
    private final InterviewActivityLease activityLease;
    private final Clock clock;

    public InterviewAnswerApplicationService(
            CurrentActorProvider currentActorProvider,
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            InterviewAnswerRepository answerRepository,
            InterviewTaskRepository taskRepository,
            InterviewActivityLease activityLease,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(
                currentActorProvider,
                "currentActorProvider不能为null"
        );
        this.sessionRepository = Objects.requireNonNull(
                sessionRepository,
                "sessionRepository不能为null"
        );
        this.questionRepository = Objects.requireNonNull(
                questionRepository,
                "questionRepository不能为null"
        );
        this.answerRepository = Objects.requireNonNull(
                answerRepository,
                "answerRepository不能为null"
        );
        this.taskRepository = Objects.requireNonNull(
                taskRepository,
                "taskRepository不能为null"
        );
        this.activityLease = Objects.requireNonNull(
                activityLease,
                "activityLease不能为null"
        );
        this.clock = Objects.requireNonNull(clock, "clock不能为null");
    }

    @Transactional
    public DraftResult saveDraft(SaveDraftCommand command) {
        Objects.requireNonNull(command, "command不能为null");
        ActorId ownerId = currentActorProvider.currentActor();
        InterviewAggregate aggregate = findOwned(
                ownerId,
                command.interviewId()
        );
        requireInterviewing(aggregate);
        requireCurrentPresentedQuestion(
                ownerId,
                command.interviewId(),
                command.questionId()
        );

        Optional<AnswerRecord> current = answerRepository.findByQuestion(
                ownerId,
                command.interviewId(),
                command.questionId()
        );
        if (current.isEmpty()) {
            requireInitialVersion(command.expectedVersion());
            InterviewAnswer answer = InterviewAnswer.draft(
                    UUID.randomUUID(),
                    command.questionId()
            ).saveDraft(command.content());
            AnswerRecord inserted = draftRecord(answer, 0);
            answerRepository.insert(
                    ownerId,
                    command.interviewId(),
                    inserted
            );
            return draftResult(command.interviewId(), inserted);
        }

        AnswerRecord stored = current.get();
        requireDraft(stored);
        requireAnswerVersion(stored, command.expectedVersion());
        if (stored.answer().draftContent().equals(command.content())) {
            return draftResult(command.interviewId(), stored);
        }
        AnswerRecord updated = draftRecord(
                stored.answer().saveDraft(command.content()),
                Math.addExact(stored.version(), 1)
        );
        if (!answerRepository.updateIfVersionMatches(
                ownerId,
                command.interviewId(),
                updated,
                stored.version()
        )) {
            throw currentAnswerVersionConflict(
                    ownerId,
                    command.interviewId(),
                    command.questionId()
            );
        }
        return draftResult(command.interviewId(), updated);
    }

    @Transactional
    public SubmissionResult submit(SubmitAnswerCommand command) {
        Objects.requireNonNull(command, "command不能为null");
        String fingerprint = fingerprint(
                "SUBMIT",
                command.interviewId(),
                command.questionId(),
                command.content()
        );
        return completeQuestion(
                command.interviewId(),
                command.questionId(),
                command.requestId(),
                command.expectedVersion(),
                command.content(),
                fingerprint,
                false
        );
    }

    @Transactional
    public SubmissionResult skip(SkipQuestionCommand command) {
        Objects.requireNonNull(command, "command不能为null");
        String fingerprint = fingerprint(
                "SKIP",
                command.interviewId(),
                command.questionId(),
                ""
        );
        return completeQuestion(
                command.interviewId(),
                command.questionId(),
                command.requestId(),
                command.expectedVersion(),
                null,
                fingerprint,
                true
        );
    }

    private SubmissionResult completeQuestion(
            UUID interviewId,
            UUID questionId,
            UUID requestId,
            long expectedAnswerVersion,
            String submittedContent,
            String requestFingerprint,
            boolean skipped
    ) {
        ActorId ownerId = currentActorProvider.currentActor();
        Optional<AnswerRecord> replay =
                answerRepository.findAnswerByRequestId(ownerId, requestId);
        if (replay.isPresent()) {
            AnswerRecord existing = replay.get();
            if (!requestFingerprint.equals(
                    existing.submitRequestFingerprint()
            ) || !questionId.equals(existing.answer().questionId())) {
                throw new IllegalStateException(
                        "同requestId不能对应不同的答案请求"
                );
            }
            InterviewAggregate current = findOwned(ownerId, interviewId);
            return submissionResult(current, existing);
        }

        InterviewAggregate aggregate = findOwned(ownerId, interviewId);
        requireInterviewing(aggregate);
        QuestionRecord questionRecord = requireCurrentPresentedQuestion(
                ownerId,
                interviewId,
                questionId
        );
        Optional<AnswerRecord> stored = answerRepository.findByQuestion(
                ownerId,
                interviewId,
                questionId
        );
        AnswerRecord completed = skipped
                ? discardedRecord(
                        prepareDraft(stored, questionId, expectedAnswerVersion),
                        requestId,
                        requestFingerprint,
                        clock.instant(),
                        stored.isPresent()
                )
                : submittedRecord(
                        prepareDraft(stored, questionId, expectedAnswerVersion),
                        submittedContent,
                        requestId,
                        requestFingerprint,
                        clock.instant(),
                        stored.isPresent()
                );

        Instant now = clock.instant();
        TransitionResult transition = stateMachineFor(aggregate).transition(
                aggregate,
                skipped
                        ? InterviewEvent.ANSWER_SKIPPED
                        : InterviewEvent.ANSWER_SUBMITTED,
                now
        );
        InterviewQuestion finishedQuestion = skipped
                ? questionRecord.question().skip()
                : questionRecord.question().close();

        if (!questionRepository.updateIfVersionMatches(
                ownerId,
                interviewId,
                finishedQuestion,
                questionRecord.version()
        )) {
            throw stateConflict();
        }
        persistCompletedAnswer(
                ownerId,
                interviewId,
                stored,
                completed
        );
        InterviewAggregate updated = transition.aggregate();
        if (!sessionRepository.updateIfVersionMatches(
                ownerId,
                updated,
                aggregate.session().version()
        )) {
            throw stateConflict();
        }
        enqueueNextTask(
                ownerId,
                updated,
                completed,
                transition.shouldGenerateReport(),
                now
        );
        revokeLeaseQuietly(ownerId, interviewId);
        return submissionResult(updated, completed);
    }

    private AnswerRecord prepareDraft(
            Optional<AnswerRecord> stored,
            UUID questionId,
            long expectedVersion
    ) {
        if (stored.isEmpty()) {
            requireInitialVersion(expectedVersion);
            return draftRecord(
                    InterviewAnswer.draft(UUID.randomUUID(), questionId),
                    0
            );
        }
        AnswerRecord current = stored.get();
        requireDraft(current);
        requireAnswerVersion(current, expectedVersion);
        return current;
    }

    private AnswerRecord submittedRecord(
            AnswerRecord draft,
            String content,
            UUID requestId,
            String requestFingerprint,
            Instant submittedAt,
            boolean update
    ) {
        InterviewAnswer submitted = draft.answer()
                .saveDraft(content)
                .submit();
        return new AnswerRecord(
                submitted,
                requestId,
                requestFingerprint,
                sha256(content),
                null,
                null,
                null,
                submittedAt,
                update ? Math.addExact(draft.version(), 1) : 0
        );
    }

    private AnswerRecord discardedRecord(
            AnswerRecord draft,
            UUID requestId,
            String requestFingerprint,
            Instant submittedAt,
            boolean update
    ) {
        return new AnswerRecord(
                draft.answer().discard(),
                requestId,
                requestFingerprint,
                null,
                null,
                null,
                null,
                submittedAt,
                update ? Math.addExact(draft.version(), 1) : 0
        );
    }

    private void persistCompletedAnswer(
            ActorId ownerId,
            UUID interviewId,
            Optional<AnswerRecord> stored,
            AnswerRecord completed
    ) {
        if (stored.isEmpty()) {
            answerRepository.insert(ownerId, interviewId, completed);
            return;
        }
        if (!answerRepository.updateIfVersionMatches(
                ownerId,
                interviewId,
                completed,
                stored.get().version()
        )) {
            throw currentAnswerVersionConflict(
                    ownerId,
                    interviewId,
                    completed.answer().questionId()
            );
        }
    }

    private void enqueueNextTask(
            ActorId ownerId,
            InterviewAggregate aggregate,
            AnswerRecord answer,
            boolean shouldGenerateReport,
            Instant now
    ) {
        if (aggregate.session().stage() == SessionStage.COMPLETED) {
            return;
        }
        TaskType taskType = shouldGenerateReport
                ? TaskType.GENERATE_REPORT
                : TaskType.ANALYZE_TURN;
        String businessKey = shouldGenerateReport
                ? "report:" + aggregate.session().interviewId()
                : "analyze:" + answer.answer().answerId();
        String contextFingerprint = shouldGenerateReport
                ? fingerprint(
                        "REPORT",
                        aggregate.session().interviewId(),
                        answer.answer().questionId(),
                        Long.toString(aggregate.session().version())
                )
                : answer.submitRequestFingerprint();
        var versions = sessionRepository.findVersions(ownerId,
                aggregate.session().interviewId()).orElseThrow();
        boolean inserted = taskRepository.enqueue(new TaskDefinition(
                UUID.randomUUID(),
                aggregate.session().interviewId(),
                ownerId,
                taskType,
                businessKey,
                TASK_MAX_ATTEMPTS,
                versions.inputVersion(),
                versions.planVersion(),
                contextFingerprint,
                now
        ));
        if (!inserted) {
            throw new IllegalStateException("面试后台任务已存在");
        }
    }

    private static InterviewStateMachine stateMachineFor(InterviewAggregate aggregate) {
        return aggregate.session().mode() == InterviewMode.COMPREHENSIVE
                ? new InterviewStateMachine(new ComprehensiveInterviewPolicy(
                        aggregate.timer().duration().toSeconds()))
                : STATE_MACHINE;
    }

    private QuestionRecord requireCurrentPresentedQuestion(
            ActorId ownerId,
            UUID interviewId,
            UUID questionId
    ) {
        QuestionRecord record = questionRepository.findQuestionRecord(
                        ownerId,
                        interviewId,
                        questionId
                )
                .orElseThrow(() -> new InterviewFlowNotFoundException(
                        interviewId
                ));
        long presentedCount = questionRepository.findQuestions(
                        ownerId,
                        interviewId
                ).stream()
                .filter(question -> question.status()
                        == QuestionStatus.PRESENTED)
                .count();
        if (record.question().status() != QuestionStatus.PRESENTED
                || presentedCount != 1) {
            throw new IllegalStateException(
                    "只有当前唯一PRESENTED问题可以作答"
            );
        }
        return record;
    }

    private InterviewAggregate findOwned(
            ActorId ownerId,
            UUID interviewId
    ) {
        return sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new InterviewFlowNotFoundException(
                        interviewId
                ));
    }

    private void requireInterviewing(InterviewAggregate aggregate) {
        if (aggregate.session().stage() != SessionStage.INTERVIEWING) {
            throw new IllegalStateException("只有正式进行中的面试可以作答");
        }
    }

    private void requireDraft(AnswerRecord answer) {
        if (answer.answer().status() != AnswerStatus.DRAFT) {
            throw new IllegalStateException("已冻结或已跳过的答案不能修改");
        }
    }

    private void requireAnswerVersion(
            AnswerRecord answer,
            long expectedVersion
    ) {
        if (answer.version() != expectedVersion) {
            throw new InterviewAnswerVersionConflictException(
                    answer.version()
            );
        }
    }

    private void requireInitialVersion(long expectedVersion) {
        if (expectedVersion != 0) {
            throw new InterviewAnswerVersionConflictException(0);
        }
    }

    private InterviewAnswerVersionConflictException
    currentAnswerVersionConflict(
            ActorId ownerId,
            UUID interviewId,
            UUID questionId
    ) {
        long currentVersion = answerRepository.findByQuestion(
                        ownerId,
                        interviewId,
                        questionId
                )
                .map(AnswerRecord::version)
                .orElse(0L);
        return new InterviewAnswerVersionConflictException(currentVersion);
    }

    private IllegalStateException stateConflict() {
        return new IllegalStateException("面试状态已变化，请刷新后重试");
    }

    private AnswerRecord draftRecord(
            InterviewAnswer answer,
            long version
    ) {
        return new AnswerRecord(
                answer,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                version
        );
    }

    private DraftResult draftResult(
            UUID interviewId,
            AnswerRecord answer
    ) {
        return new DraftResult(
                interviewId,
                answer.answer().questionId(),
                answer.answer().answerId(),
                answer.answer().draftContent(),
                answer.answer().status(),
                answer.version()
        );
    }

    private SubmissionResult submissionResult(
            InterviewAggregate aggregate,
            AnswerRecord answer
    ) {
        return new SubmissionResult(
                aggregate.session().interviewId(),
                answer.answer().questionId(),
                answer.answer().answerId(),
                answer.answer().status(),
                answer.version(),
                aggregate.session().stage(),
                aggregate.timer().state(),
                aggregate.session().version(),
                nextAction(aggregate)
        );
    }

    private NextAction nextAction(InterviewAggregate aggregate) {
        return switch (aggregate.session().stage()) {
            case GENERATING_REPORT -> NextAction.GENERATING_REPORT;
            case COMPLETED -> NextAction.COMPLETED;
            default -> NextAction.ANALYZING;
        };
    }

    private void revokeLeaseQuietly(ActorId ownerId, UUID interviewId) {
        try {
            activityLease.revoke(ownerId, interviewId);
        } catch (RuntimeException exception) {
            log.warn(
                    "答案已持久化，但面试活动租约删除失败，interviewId={}",
                    interviewId
            );
        }
    }

    private static String fingerprint(
            String operation,
            UUID interviewId,
            UUID questionId,
            String content
    ) {
        return sha256(operation
                + "\n" + interviewId
                + "\n" + questionId
                + "\n" + content);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            ));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JVM不支持SHA-256", exception);
        }
    }

    public enum NextAction {
        ANALYZING,
        GENERATING_REPORT,
        COMPLETED
    }

    public record DraftResult(
            UUID interviewId,
            UUID questionId,
            UUID answerId,
            String content,
            AnswerStatus status,
            long version
    ) {

        public DraftResult {
            Objects.requireNonNull(interviewId, "interviewId不能为null");
            Objects.requireNonNull(questionId, "questionId不能为null");
            Objects.requireNonNull(answerId, "answerId不能为null");
            Objects.requireNonNull(content, "content不能为null");
            Objects.requireNonNull(status, "status不能为null");
            if (version < 0) {
                throw new IllegalArgumentException("version不能小于0");
            }
        }
    }

    public record SubmissionResult(
            UUID interviewId,
            UUID questionId,
            UUID answerId,
            AnswerStatus answerStatus,
            long answerVersion,
            SessionStage sessionStage,
            TimerState timerState,
            long sessionVersion,
            NextAction nextAction
    ) {

        public SubmissionResult {
            Objects.requireNonNull(interviewId, "interviewId不能为null");
            Objects.requireNonNull(questionId, "questionId不能为null");
            Objects.requireNonNull(answerId, "answerId不能为null");
            Objects.requireNonNull(answerStatus, "answerStatus不能为null");
            Objects.requireNonNull(sessionStage, "sessionStage不能为null");
            Objects.requireNonNull(timerState, "timerState不能为null");
            Objects.requireNonNull(nextAction, "nextAction不能为null");
            if (answerVersion < 0 || sessionVersion < 0) {
                throw new IllegalArgumentException("答案或会话版本不合法");
            }
        }
    }
}
