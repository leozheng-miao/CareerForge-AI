package com.leo.careerforgeai.interviewflow.application.answer;

import com.leo.careerforgeai.interviewflow.application.port.InterviewActivityLease;
import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTaskRepository;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewEvent;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewSession;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewStateMachine;
import com.leo.careerforgeai.interviewflow.domain.session.QuickInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
import com.leo.careerforgeai.interviewflow.domain.timer.TimerState;
import com.leo.careerforgeai.interviewflow.domain.turn.AnswerStatus;
import com.leo.careerforgeai.interviewflow.domain.turn.InterviewAnswer;
import com.leo.careerforgeai.interviewflow.domain.turn.InterviewQuestion;
import com.leo.careerforgeai.interviewflow.domain.turn.QuestionStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证草稿、冻结提交、跳过和耗尽后收口的原子业务规则
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
class InterviewAnswerApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("quick-owner-a");
    private static final UUID INTERVIEW_ID =
            UUID.fromString("36000000-0000-0000-0000-000000000001");
    private static final UUID TOPIC_ID =
            UUID.fromString("36000000-0000-0000-0000-000000000002");
    private static final UUID QUESTION_ID =
            UUID.fromString("36000000-0000-0000-0000-000000000003");
    private static final UUID OTHER_QUESTION_ID =
            UUID.fromString("36000000-0000-0000-0000-000000000004");
    private static final UUID REQUEST_ID =
            UUID.fromString("36000000-0000-0000-0000-000000000005");
    private static final Instant NOW =
            Instant.parse("2026-10-06T06:00:00Z");

    private final InMemorySessionRepository sessionRepository =
            new InMemorySessionRepository();
    private final InMemoryQuestionRepository questionRepository =
            new InMemoryQuestionRepository();
    private final InMemoryAnswerRepository answerRepository =
            new InMemoryAnswerRepository();
    private final InMemoryTaskRepository taskRepository =
            new InMemoryTaskRepository();
    private final FakeActivityLease activityLease =
            new FakeActivityLease();
    private final MutableClock clock = new MutableClock(NOW);
    private InterviewAnswerApplicationService service;

    @BeforeEach
    void setUp() {
        sessionRepository.seed(runningInterview());
        questionRepository.seed(presentedQuestion(QUESTION_ID), 1);
        questionRepository.seed(reservedQuestion(OTHER_QUESTION_ID), 0);
        service = new InterviewAnswerApplicationService(
                () -> OWNER,
                sessionRepository,
                questionRepository,
                answerRepository,
                taskRepository,
                activityLease,
                clock
        );
    }

    @Test
    void shouldSaveUpToEightThousandCharactersOnlyForCurrentQuestion() {
        String content = "a".repeat(8000);

        var result = service.saveDraft(new SaveDraftCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                content,
                0
        ));

        assertThat(result.content()).isEqualTo(content);
        assertThat(result.version()).isZero();
        assertThat(result.status()).isEqualTo(AnswerStatus.DRAFT);
        assertThat(sessionRepository.updateCount).isZero();
        assertThat(taskRepository.tasks).isEmpty();
        assertThatThrownBy(() -> service.saveDraft(new SaveDraftCommand(
                INTERVIEW_ID,
                OTHER_QUESTION_ID,
                "不应被保存",
                0
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PRESENTED");
    }

    @Test
    void shouldRejectContentLongerThanEightThousandCharacters() {
        assertThatThrownBy(() -> new SaveDraftCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                "a".repeat(8001),
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8000");
    }

    @Test
    void shouldProtectNewerDraftWithExpectedVersion() {
        var first = service.saveDraft(new SaveDraftCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                "第一版",
                0
        ));
        var second = service.saveDraft(new SaveDraftCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                "第二版",
                first.version()
        ));

        assertThat(second.version()).isEqualTo(1);
        assertThatThrownBy(() -> service.saveDraft(new SaveDraftCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                "过期覆盖",
                first.version()
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("版本");
        assertThat(answerRepository.byQuestion.get(QUESTION_ID)
                .answer().draftContent()).isEqualTo("第二版");
    }

    @Test
    void shouldFreezeAnswerSettleTimerAndEnqueueOneAnalysisTask() {
        var draft = service.saveDraft(new SaveDraftCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                "我会将Agent决策与业务状态机隔离。",
                0
        ));
        clock.advance(Duration.ofSeconds(30));
        SubmitAnswerCommand command = new SubmitAnswerCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                REQUEST_ID,
                "我会将Agent决策与业务状态机隔离。",
                draft.version()
        );

        var first = service.submit(command);
        var replay = service.submit(command);

        var storedAnswer = answerRepository.byQuestion.get(QUESTION_ID);
        assertThat(storedAnswer.answer().status())
                .isEqualTo(AnswerStatus.SUBMITTED);
        assertThat(storedAnswer.answer().submittedContent())
                .isEqualTo("我会将Agent决策与业务状态机隔离。");
        assertThat(questionRepository.records.get(QUESTION_ID)
                .question().status()).isEqualTo(QuestionStatus.CLOSED);
        assertThat(sessionRepository.aggregate.timer().state())
                .isEqualTo(TimerState.PAUSED);
        assertThat(sessionRepository.aggregate.timer().accumulated())
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(taskRepository.tasks).hasSize(1);
        assertThat(taskRepository.tasks.getFirst().taskType())
                .isEqualTo(InterviewTaskRepository.TaskType.ANALYZE_TURN);
        assertThat(activityLease.revokeCount).isEqualTo(1);
        assertThat(replay.answerId()).isEqualTo(first.answerId());
        assertThat(sessionRepository.updateCount).isEqualTo(1);
    }

    @Test
    void shouldRejectEditingOrResubmittingAFrozenAnswer() {
        var submitted = service.submit(new SubmitAnswerCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                REQUEST_ID,
                "已冻结回答",
                0
        ));

        assertThat(submitted.answerStatus())
                .isEqualTo(AnswerStatus.SUBMITTED);
        assertThatThrownBy(() -> service.saveDraft(new SaveDraftCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                "试图修改",
                submitted.answerVersion()
        ))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.submit(new SubmitAnswerCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                UUID.fromString(
                        "36000000-0000-0000-0000-000000000006"
                ),
                "另一个回答",
                submitted.answerVersion()
        ))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectReusedRequestIdWithDifferentSubmission() {
        service.submit(new SubmitAnswerCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                REQUEST_ID,
                "原始回答",
                0
        ));

        assertThatThrownBy(() -> service.submit(new SubmitAnswerCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                REQUEST_ID,
                "篡改回答",
                0
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requestId");
    }

    @Test
    void shouldPersistSkipAndEnqueueOneAnalysisTask() {
        var result = service.skip(new SkipQuestionCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                REQUEST_ID,
                0
        ));

        assertThat(result.answerStatus()).isEqualTo(AnswerStatus.DISCARDED);
        assertThat(result.nextAction())
                .isEqualTo(InterviewAnswerApplicationService.NextAction.ANALYZING);
        assertThat(questionRepository.records.get(QUESTION_ID)
                .question().status()).isEqualTo(QuestionStatus.SKIPPED);
        assertThat(sessionRepository.aggregate.submittedAnswerCount())
                .isZero();
        assertThat(taskRepository.tasks).hasSize(1);
        assertThat(taskRepository.tasks.getFirst().taskType())
                .isEqualTo(InterviewTaskRepository.TaskType.ANALYZE_TURN);
    }

    @Test
    void shouldGenerateReportAfterSubmittingTheExhaustedCurrentQuestion() {
        sessionRepository.seed(exhaustedInterview());

        var result = service.submit(new SubmitAnswerCommand(
                INTERVIEW_ID,
                QUESTION_ID,
                REQUEST_ID,
                "时间用尽后完整提交当前题。",
                0
        ));

        assertThat(result.nextAction()).isEqualTo(
                InterviewAnswerApplicationService.NextAction.GENERATING_REPORT
        );
        assertThat(sessionRepository.aggregate.session().stage())
                .isEqualTo(SessionStage.GENERATING_REPORT);
        assertThat(sessionRepository.aggregate.timer().state())
                .isEqualTo(TimerState.STOPPED);
        assertThat(taskRepository.tasks).hasSize(1);
        assertThat(taskRepository.tasks.getFirst().taskType())
                .isEqualTo(InterviewTaskRepository.TaskType.GENERATE_REPORT);
    }

    private InterviewAggregate runningInterview() {
        InterviewStateMachine machine = new InterviewStateMachine(
                new QuickInterviewPolicy()
        );
        InterviewAggregate aggregate = InterviewAggregate.createQuick(
                INTERVIEW_ID,
                OWNER,
                NOW,
                new QuickInterviewPolicy()
        );
        aggregate = machine.transition(
                aggregate,
                InterviewEvent.PREPARATION_COMPLETED,
                NOW
        ).aggregate();
        return machine.transition(
                aggregate,
                InterviewEvent.FORMAL_START_REQUESTED,
                NOW
        ).aggregate();
    }

    private InterviewAggregate exhaustedInterview() {
        return new InterviewStateMachine(new QuickInterviewPolicy())
                .transition(
                        runningInterview(),
                        InterviewEvent.TIMER_EXHAUSTED,
                        NOW
                )
                .aggregate();
    }

    private InterviewQuestion presentedQuestion(UUID questionId) {
        return reservedQuestion(questionId).present();
    }

    private InterviewQuestion reservedQuestion(UUID questionId) {
        return InterviewQuestion.reserved(
                questionId,
                TOPIC_ID,
                InterviewQuestion.QuestionType.MAIN,
                questionId.equals(QUESTION_ID) ? 1 : 2,
                "请设计一个受限的Agent工作流。"
        );
    }

    private static final class InMemorySessionRepository
            implements InterviewSessionRepository {

        private InterviewAggregate aggregate;
        private int updateCount;

        void seed(InterviewAggregate value) {
            aggregate = value;
            updateCount = 0;
        }

        @Override
        public InterviewAggregate claim(SessionClaim claim) {
            aggregate = claim.candidate();
            return aggregate;
        }

        @Override
        public Optional<InterviewAggregate> findById(
                ActorId ownerId,
                UUID interviewId
        ) {
            if (aggregate == null
                    || !aggregate.session().ownerId().equals(ownerId)
                    || !aggregate.session().interviewId().equals(interviewId)) {
                return Optional.empty();
            }
            return Optional.of(aggregate);
        }

        @Override
        public Optional<InterviewAggregate> findByRequestId(
                ActorId ownerId,
                UUID requestId
        ) {
            return Optional.empty();
        }

        @Override
        public Optional<InterviewSession> findInProgress(ActorId ownerId) {
            return Optional.ofNullable(aggregate)
                    .map(InterviewAggregate::session);
        }

        @Override
        public boolean updateIfVersionMatches(
                ActorId ownerId,
                InterviewAggregate updated,
                long expectedVersion
        ) {
            if (aggregate.session().version() != expectedVersion) {
                return false;
            }
            aggregate = updated;
            updateCount++;
            return true;
        }
    }

    private static final class InMemoryQuestionRepository
            implements InterviewQuestionRepository {

        private final Map<UUID, QuestionRecord> records =
                new LinkedHashMap<>();

        void seed(InterviewQuestion question, long version) {
            records.put(
                    question.questionId(),
                    new QuestionRecord(question, version)
            );
        }

        @Override
        public void insertAll(
                ActorId ownerId,
                UUID interviewId,
                List<QuestionWrite> questions
        ) {
            for (QuestionWrite write : questions) {
                seed(write.question(), 0);
            }
        }

        @Override
        public Optional<InterviewQuestion> findQuestion(
                ActorId ownerId,
                UUID interviewId,
                UUID questionId
        ) {
            return Optional.ofNullable(records.get(questionId))
                    .map(QuestionRecord::question);
        }

        @Override
        public Optional<QuestionRecord> findQuestionRecord(
                ActorId ownerId,
                UUID interviewId,
                UUID questionId
        ) {
            return Optional.ofNullable(records.get(questionId));
        }

        @Override
        public List<InterviewQuestion> findQuestions(
                ActorId ownerId,
                UUID interviewId
        ) {
            return records.values().stream()
                    .map(QuestionRecord::question)
                    .toList();
        }

        @Override
        public boolean updateIfVersionMatches(
                ActorId ownerId,
                UUID interviewId,
                InterviewQuestion updated,
                long expectedVersion
        ) {
            QuestionRecord current = records.get(updated.questionId());
            if (current == null || current.version() != expectedVersion) {
                return false;
            }
            records.put(
                    updated.questionId(),
                    new QuestionRecord(updated, expectedVersion + 1)
            );
            return true;
        }
    }

    private static final class InMemoryAnswerRepository
            implements InterviewAnswerRepository {

        private final Map<UUID, AnswerRecord> byQuestion =
                new LinkedHashMap<>();
        private final Map<UUID, AnswerRecord> byRequest =
                new LinkedHashMap<>();

        @Override
        public void insert(
                ActorId ownerId,
                UUID interviewId,
                AnswerRecord answer
        ) {
            if (byQuestion.putIfAbsent(
                    answer.answer().questionId(),
                    answer
            ) != null) {
                throw new IllegalStateException("答案已存在");
            }
            indexRequest(answer);
        }

        @Override
        public Optional<AnswerRecord> findByQuestion(
                ActorId ownerId,
                UUID interviewId,
                UUID questionId
        ) {
            return Optional.ofNullable(byQuestion.get(questionId));
        }

        @Override
        public Optional<AnswerRecord> findAnswerByRequestId(
                ActorId ownerId,
                UUID requestId
        ) {
            return Optional.ofNullable(byRequest.get(requestId));
        }

        @Override
        public boolean updateIfVersionMatches(
                ActorId ownerId,
                UUID interviewId,
                AnswerRecord updated,
                long expectedVersion
        ) {
            AnswerRecord current = byQuestion.get(
                    updated.answer().questionId()
            );
            if (current == null || current.version() != expectedVersion) {
                return false;
            }
            byQuestion.put(updated.answer().questionId(), updated);
            indexRequest(updated);
            return true;
        }

        private void indexRequest(AnswerRecord answer) {
            if (answer.submitRequestId() != null) {
                byRequest.put(answer.submitRequestId(), answer);
            }
        }
    }

    private static final class InMemoryTaskRepository
            implements InterviewTaskRepository {

        private final List<TaskDefinition> tasks = new ArrayList<>();

        @Override
        public boolean enqueue(TaskDefinition task) {
            if (tasks.stream().anyMatch(existing -> existing.businessKey()
                    .equals(task.businessKey()))) {
                return false;
            }
            tasks.add(task);
            return true;
        }

        @Override
        public Optional<ClaimedTask> claimNextAvailableTask(
                Instant now,
                String leaseOwner,
                Instant leaseUntil
        ) {
            return Optional.empty();
        }
    }

    private static final class FakeActivityLease
            implements InterviewActivityLease {

        private int revokeCount;

        @Override
        public void refresh(
                ActorId ownerId,
                UUID interviewId,
                Duration leaseDuration
        ) {
        }

        @Override
        public boolean isActive(ActorId ownerId, UUID interviewId) {
            return false;
        }

        @Override
        public void revoke(ActorId ownerId, UUID interviewId) {
            revokeCount++;
        }
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
