package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.interview.application.answer.InterviewAnswerSubmissionService;
import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.question.InterviewQuestionGenerationService;
import com.leo.careerforgeai.interview.domain.InterviewAnswer;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewReviewPlan;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewRoundStatus;
import com.leo.careerforgeai.interview.domain.InterviewRouteDecision;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.InterviewWaitReason;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

/**
 * @program: CareerForge-AI
 * @description: 验证固定threadId下多轮HITL、并行评审、幂等重放和失败Checkpoint恢复
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
class InterviewGraphExecutionServiceTest {

    private static final ActorId OWNER = new ActorId("graph-owner");
    private static final UUID INTERVIEW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FIRST_ROUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID FIRST_QUESTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ANSWER_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID TECHNICAL_REVIEW_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID EVIDENCE_REVIEW_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID SECOND_ROUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000008");
    private static final UUID SECOND_QUESTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final String ANSWER_TEXT = "虚拟线程适合大量阻塞任务。";
    private static final String SNAPSHOT_HASH = "a".repeat(64);
    private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(30);

    @Test
    void shouldStartSubmitResumeAndReplayAtSecondQuestionWithoutRepeatingCompletedNodes() throws Exception {
        MockInterviewSessionRepository sessionRepository = mock(MockInterviewSessionRepository.class);
        InterviewRoundRepository roundRepository = mock(InterviewRoundRepository.class);
        InterviewQuestionGenerationService generationService = mock(InterviewQuestionGenerationService.class);
        InterviewAnswerSubmissionService answerService = mock(InterviewAnswerSubmissionService.class);
        InterviewReviewGraphNodes reviewNodes = mock(InterviewReviewGraphNodes.class);
        InterviewRouteGraphNodes routeNodes = nextQuestionRouteNodes();
        AtomicReference<MockInterviewSession> session = new AtomicReference<>(session(InterviewStatus.CREATED));

        configureBusinessFacts(sessionRepository, roundRepository, generationService, answerService, session);
        configureSuccessfulReviews(reviewNodes);

        InterviewGraphNodes nodes = spy(new InterviewGraphNodes(
                () -> OWNER, sessionRepository, roundRepository, generationService, MODEL_TIMEOUT
        ));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var graph = new InterviewGraphWorkflow(
                    nodes,
                    reviewNodes,
                    nextQuestionSupervisionNode(),
                    routeNodes,
                    mock(InterviewReportGraphNode.class)
            ).compile(new MemorySaver());
            var service = new InterviewGraphExecutionService(
                    () -> OWNER, sessionRepository, answerService, graph, executor
            );

            InterviewGraphState waiting = service.start(INTERVIEW_ID);
            session.set(session(InterviewStatus.WAITING_FOR_ANSWER));
            InterviewGraphState startReplay = service.start(INTERVIEW_ID);

            assertThat(waiting.currentQuestionId()).contains(FIRST_QUESTION_ID);
            assertThat(waiting.waitReason()).contains(InterviewWaitReason.WAITING_FOR_ANSWER);
            assertThat(startReplay.data()).isEqualTo(waiting.data());

            session.set(session(InterviewStatus.REVIEWING));
            InterviewGraphState waitingForSecondAnswer = service.submitAnswerAndResume(
                    INTERVIEW_ID, 1, FIRST_QUESTION_ID, REQUEST_ID, 2, ANSWER_TEXT
            );
            InterviewGraphState resumeReplay = service.submitAnswerAndResume(
                    INTERVIEW_ID, 1, FIRST_QUESTION_ID, REQUEST_ID, 2, ANSWER_TEXT
            );

            assertThat(waitingForSecondAnswer.currentRound()).isEqualTo(2);
            assertThat(waitingForSecondAnswer.currentQuestionId()).contains(SECOND_QUESTION_ID);
            assertThat(waitingForSecondAnswer.waitReason()).contains(InterviewWaitReason.WAITING_FOR_ANSWER);
            assertThat(waitingForSecondAnswer.answerId()).isEmpty();
            assertThat(waitingForSecondAnswer.routeDecision()).isEmpty();
            assertThat(waitingForSecondAnswer.data()).doesNotContainKeys(
                    InterviewGraphState.REVIEW_PLAN,
                    InterviewGraphState.TECHNICAL_REVIEW_ID,
                    InterviewGraphState.EVIDENCE_REVIEW_ID
            );
            assertThat(resumeReplay.data()).isEqualTo(waitingForSecondAnswer.data());

            verify(generationService).generateAndPersistQuestion(INTERVIEW_ID, 1, null, MODEL_TIMEOUT);
            verify(generationService).generateAndPersistQuestion(
                    INTERVIEW_ID, 2, InterviewRouteDecision.NEXT_QUESTION, MODEL_TIMEOUT
            );
            verify(nodes).validateAnswerResume(any(InterviewGraphState.class));
            verify(reviewNodes).technicalReview(any(InterviewGraphState.class));
            verify(reviewNodes).evidenceReview(any(InterviewGraphState.class));
            verify(reviewNodes).joinReviews(any(InterviewGraphState.class));
            verify(routeNodes).continueQuestioning(any(InterviewGraphState.class));
            verify(answerService, times(2))
                    .submit(INTERVIEW_ID, 1, FIRST_QUESTION_ID, REQUEST_ID, 2, ANSWER_TEXT);
        }
    }

    @Test
    void shouldResumeIncompleteParallelCheckpointAndReachSecondQuestion() throws Exception {
        MockInterviewSessionRepository sessionRepository = mock(MockInterviewSessionRepository.class);
        InterviewRoundRepository roundRepository = mock(InterviewRoundRepository.class);
        InterviewQuestionGenerationService generationService = mock(InterviewQuestionGenerationService.class);
        InterviewAnswerSubmissionService answerService = mock(InterviewAnswerSubmissionService.class);
        InterviewReviewGraphNodes reviewNodes = mock(InterviewReviewGraphNodes.class);
        InterviewRouteGraphNodes routeNodes = nextQuestionRouteNodes();
        AtomicReference<MockInterviewSession> session = new AtomicReference<>(session(InterviewStatus.CREATED));
        CountDownLatch technicalCompleted = new CountDownLatch(1);
        AtomicInteger evidenceAttempts = new AtomicInteger();

        configureBusinessFacts(sessionRepository, roundRepository, generationService, answerService, session);
        when(reviewNodes.prepareReviews(any(InterviewGraphState.class)))
                .thenReturn(Map.of(
                        InterviewGraphState.REVIEW_PLAN,
                        InterviewReviewPlan.TECHNICAL_AND_EVIDENCE.name()
                ));
        when(reviewNodes.technicalReview(any(InterviewGraphState.class))).thenAnswer(invocation -> {
            technicalCompleted.countDown();
            return Map.of(InterviewGraphState.TECHNICAL_REVIEW_ID, TECHNICAL_REVIEW_ID.toString());
        });
        when(reviewNodes.evidenceReview(any(InterviewGraphState.class))).thenAnswer(invocation -> {
            if (!technicalCompleted.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("技术评审分支未在期限内完成");
            }
            if (evidenceAttempts.incrementAndGet() == 1) {
                throw new IllegalStateException("模拟证据评审首次失败");
            }
            return Map.of(InterviewGraphState.EVIDENCE_REVIEW_ID, EVIDENCE_REVIEW_ID.toString());
        });
        when(reviewNodes.joinReviews(any(InterviewGraphState.class))).thenReturn(Map.of());

        InterviewGraphNodes nodes = spy(new InterviewGraphNodes(
                () -> OWNER, sessionRepository, roundRepository, generationService, MODEL_TIMEOUT
        ));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var graph = new InterviewGraphWorkflow(
                    nodes,
                    reviewNodes,
                    nextQuestionSupervisionNode(),
                    routeNodes,
                    mock(InterviewReportGraphNode.class)
            ).compile(new MemorySaver());
            var service = new InterviewGraphExecutionService(
                    () -> OWNER, sessionRepository, answerService, graph, executor
            );

            service.start(INTERVIEW_ID);
            session.set(session(InterviewStatus.REVIEWING));

            assertThatThrownBy(() -> service.submitAnswerAndResume(
                    INTERVIEW_ID, 1, FIRST_QUESTION_ID, REQUEST_ID, 2, ANSWER_TEXT
            )).isInstanceOf(RuntimeException.class);

            InterviewGraphState recovered = service.submitAnswerAndResume(
                    INTERVIEW_ID, 1, FIRST_QUESTION_ID, REQUEST_ID, 2, ANSWER_TEXT
            );

            assertThat(recovered.currentRound()).isEqualTo(2);
            assertThat(recovered.currentQuestionId()).contains(SECOND_QUESTION_ID);
            assertThat(recovered.waitReason()).contains(InterviewWaitReason.WAITING_FOR_ANSWER);
            assertThat(recovered.answerId()).isEmpty();
            assertThat(recovered.routeDecision()).isEmpty();

            verify(nodes).validateAnswerResume(any(InterviewGraphState.class));
            verify(reviewNodes).prepareReviews(any(InterviewGraphState.class));
            verify(reviewNodes, times(2)).technicalReview(any(InterviewGraphState.class));
            verify(reviewNodes, times(2)).evidenceReview(any(InterviewGraphState.class));
            verify(reviewNodes).joinReviews(any(InterviewGraphState.class));
            verify(generationService).generateAndPersistQuestion(
                    INTERVIEW_ID, 2, InterviewRouteDecision.NEXT_QUESTION, MODEL_TIMEOUT
            );
            verify(answerService, times(2))
                    .submit(INTERVIEW_ID, 1, FIRST_QUESTION_ID, REQUEST_ID, 2, ANSWER_TEXT);
        }
    }

    @Test
    void shouldRecoverFromPersistedAnswerWhenCheckpointStillWaitsForAnswer() throws Exception {
        MockInterviewSessionRepository sessionRepository = mock(MockInterviewSessionRepository.class);
        InterviewRoundRepository roundRepository = mock(InterviewRoundRepository.class);
        InterviewQuestionGenerationService generationService = mock(InterviewQuestionGenerationService.class);
        InterviewAnswerSubmissionService answerService = mock(InterviewAnswerSubmissionService.class);
        InterviewReviewGraphNodes reviewNodes = mock(InterviewReviewGraphNodes.class);
        InterviewRouteGraphNodes routeNodes = nextQuestionRouteNodes();
        AtomicReference<MockInterviewSession> session = new AtomicReference<>(session(InterviewStatus.CREATED));
        InterviewAnswer answer = answer();

        configureBusinessFacts(sessionRepository, roundRepository, generationService, answerService, session);
        configureSuccessfulReviews(reviewNodes);
        when(answerService.requireSubmittedAnswer(INTERVIEW_ID, FIRST_QUESTION_ID)).thenReturn(answer);

        InterviewGraphNodes nodes = spy(new InterviewGraphNodes(
                () -> OWNER, sessionRepository, roundRepository, generationService, MODEL_TIMEOUT
        ));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var graph = new InterviewGraphWorkflow(
                    nodes,
                    reviewNodes,
                    nextQuestionSupervisionNode(),
                    routeNodes,
                    mock(InterviewReportGraphNode.class)
            ).compile(new MemorySaver());
            var service = new InterviewGraphExecutionService(
                    () -> OWNER, sessionRepository, answerService, graph, executor
            );

            service.start(INTERVIEW_ID);
            session.set(session(InterviewStatus.REVIEWING));
            service.recoverExecution(INTERVIEW_ID);
            InterviewGraphState recovered = service.start(INTERVIEW_ID);

            assertThat(recovered.currentRound()).isEqualTo(2);
            assertThat(recovered.currentQuestionId()).contains(SECOND_QUESTION_ID);
            assertThat(recovered.waitReason()).contains(InterviewWaitReason.WAITING_FOR_ANSWER);
            assertThat(recovered.answerId()).isEmpty();
            verify(answerService).requireSubmittedAnswer(INTERVIEW_ID, FIRST_QUESTION_ID);
            verify(answerService, never()).submit(
                    INTERVIEW_ID, 1, FIRST_QUESTION_ID, REQUEST_ID, 2, ANSWER_TEXT
            );
        }
    }

    private InterviewSupervisionGraphNode nextQuestionSupervisionNode() {
        InterviewSupervisionGraphNode node = mock(InterviewSupervisionGraphNode.class);
        when(node.superviseRound(any(InterviewGraphState.class)))
                .thenReturn(InterviewGraphState.routeDecisionUpdate(InterviewRouteDecision.NEXT_QUESTION));
        return node;
    }

    private InterviewRouteGraphNodes nextQuestionRouteNodes() {
        InterviewRouteGraphNodes nodes = mock(InterviewRouteGraphNodes.class);
        when(nodes.continueQuestioning(any(InterviewGraphState.class)))
                .thenReturn(InterviewGraphState.clearCompletedRoundForNextQuestionUpdate());
        return nodes;
    }

    private void configureBusinessFacts(
            MockInterviewSessionRepository sessionRepository,
            InterviewRoundRepository roundRepository,
            InterviewQuestionGenerationService generationService,
            InterviewAnswerSubmissionService answerService,
            AtomicReference<MockInterviewSession> session
    ) {
        InterviewQuestion firstQuestion = question(FIRST_QUESTION_ID, FIRST_ROUND_ID);
        InterviewQuestion secondQuestion = question(SECOND_QUESTION_ID, SECOND_ROUND_ID);
        InterviewRound firstRound = round();
        InterviewAnswer answer = answer();

        when(sessionRepository.findById(OWNER, INTERVIEW_ID)).thenAnswer(invocation -> Optional.of(session.get()));
        when(generationService.generateAndPersistQuestion(INTERVIEW_ID, 1, null, MODEL_TIMEOUT))
                .thenReturn(firstQuestion);
        when(generationService.generateAndPersistQuestion(
                INTERVIEW_ID, 2, InterviewRouteDecision.NEXT_QUESTION, MODEL_TIMEOUT
        )).thenReturn(secondQuestion);
        when(roundRepository.findRoundByNumber(OWNER, INTERVIEW_ID, 1)).thenReturn(Optional.of(firstRound));
        when(roundRepository.findQuestionByRound(OWNER, INTERVIEW_ID, FIRST_ROUND_ID))
                .thenReturn(Optional.of(firstQuestion));
        when(roundRepository.findAnswerByQuestion(OWNER, INTERVIEW_ID, FIRST_QUESTION_ID))
                .thenReturn(Optional.of(answer));
        when(answerService.submit(INTERVIEW_ID, 1, FIRST_QUESTION_ID, REQUEST_ID, 2, ANSWER_TEXT))
                .thenReturn(answer);
    }

    private void configureSuccessfulReviews(InterviewReviewGraphNodes reviewNodes) {
        when(reviewNodes.prepareReviews(any(InterviewGraphState.class)))
                .thenReturn(Map.of(InterviewGraphState.REVIEW_PLAN, InterviewReviewPlan.TECHNICAL_ONLY.name()));
        when(reviewNodes.technicalReview(any(InterviewGraphState.class)))
                .thenReturn(Map.of(InterviewGraphState.TECHNICAL_REVIEW_ID, TECHNICAL_REVIEW_ID.toString()));
        when(reviewNodes.evidenceReview(any(InterviewGraphState.class)))
                .thenReturn(Map.of(InterviewGraphState.EVIDENCE_REVIEW_ID, EVIDENCE_REVIEW_ID.toString()));
        when(reviewNodes.joinReviews(any(InterviewGraphState.class))).thenReturn(Map.of());
    }

    private MockInterviewSession session(InterviewStatus status) {
        MockInterviewSession session = mock(MockInterviewSession.class);
        when(session.interviewId()).thenReturn(INTERVIEW_ID);
        when(session.ownerId()).thenReturn(OWNER);
        when(session.mode()).thenReturn(InterviewMode.TARGETED_MOCK);
        when(session.inputSnapshotHash()).thenReturn(SNAPSHOT_HASH);
        when(session.status()).thenReturn(status);
        return session;
    }

    private InterviewQuestion question(UUID questionId, UUID roundId) {
        InterviewQuestion question = mock(InterviewQuestion.class);
        when(question.questionId()).thenReturn(questionId);
        when(question.interviewId()).thenReturn(INTERVIEW_ID);
        when(question.roundId()).thenReturn(roundId);
        when(question.ownerId()).thenReturn(OWNER);
        return question;
    }

    private InterviewRound round() {
        InterviewRound round = mock(InterviewRound.class);
        when(round.roundId()).thenReturn(FIRST_ROUND_ID);
        when(round.interviewId()).thenReturn(INTERVIEW_ID);
        when(round.ownerId()).thenReturn(OWNER);
        when(round.status()).thenReturn(InterviewRoundStatus.ANSWERED);
        return round;
    }

    private InterviewAnswer answer() {
        InterviewAnswer answer = mock(InterviewAnswer.class);
        when(answer.answerId()).thenReturn(ANSWER_ID);
        when(answer.interviewId()).thenReturn(INTERVIEW_ID);
        when(answer.roundId()).thenReturn(FIRST_ROUND_ID);
        when(answer.questionId()).thenReturn(FIRST_QUESTION_ID);
        when(answer.ownerId()).thenReturn(OWNER);
        return answer;
    }
}