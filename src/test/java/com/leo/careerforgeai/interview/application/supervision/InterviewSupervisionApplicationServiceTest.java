package com.leo.careerforgeai.interview.application.supervision;

import com.leo.careerforgeai.interview.application.port.InterviewNodeExecutionRepository;
import com.leo.careerforgeai.interview.application.port.InterviewReviewRepository;
import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.domain.EvidenceConsistencyVerdict;
import com.leo.careerforgeai.interview.domain.EvidenceReview;
import com.leo.careerforgeai.interview.domain.EvidenceReviewSource;
import com.leo.careerforgeai.interview.domain.InterviewAnswer;
import com.leo.careerforgeai.interview.domain.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewRoundStatus;
import com.leo.careerforgeai.interview.domain.InterviewRouteDecision;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.interview.domain.TechnicalReview;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证Supervisor只使用owner隔离的MySQL事实计算预算路由并拒绝错配评审
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
class InterviewSupervisionApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("supervisor-owner");
    private static final UUID INTERVIEW_ID = UUID.randomUUID();
    private static final UUID ROUND_ID = UUID.randomUUID();
    private static final UUID QUESTION_ID = UUID.randomUUID();
    private static final UUID ANSWER_ID = UUID.randomUUID();
    private static final UUID TECHNICAL_REVIEW_ID = UUID.randomUUID();
    private static final UUID EVIDENCE_REVIEW_ID = UUID.randomUUID();

    private MockInterviewSessionRepository sessionRepository;
    private InterviewRoundRepository roundRepository;
    private InterviewReviewRepository reviewRepository;
    private InterviewNodeExecutionRepository executionRepository;
    private InterviewSupervisionApplicationService service;
    private EvidenceReview evidenceReview;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(MockInterviewSessionRepository.class);
        roundRepository = mock(InterviewRoundRepository.class);
        reviewRepository = mock(InterviewReviewRepository.class);
        executionRepository = mock(InterviewNodeExecutionRepository.class);
        service = new InterviewSupervisionApplicationService(
                () -> OWNER,
                sessionRepository,
                roundRepository,
                reviewRepository,
                executionRepository,
                new InterviewSupervisor(),
                3,
                400,
                1,
                200
        );
        stubCompleteFacts();
    }

    @Test
    void shouldChooseFollowUpFromMysqlFactsAndRegisteredUsage() {
        when(roundRepository.countQuestions(OWNER, INTERVIEW_ID)).thenReturn(1);
        when(roundRepository.countFollowUps(OWNER, INTERVIEW_ID)).thenReturn(0);
        when(executionRepository.sumModelCallCount(OWNER, INTERVIEW_ID)).thenReturn(3);
        when(executionRepository.sumTotalTokens(OWNER, INTERVIEW_ID)).thenReturn(1_000L);

        InterviewSupervisorDecision decision = service.superviseRound(
                INTERVIEW_ID,
                1,
                QUESTION_ID,
                ANSWER_ID,
                TECHNICAL_REVIEW_ID,
                EVIDENCE_REVIEW_ID
        );

        assertThat(decision.routeDecision()).isEqualTo(InterviewRouteDecision.FOLLOW_UP);
        assertThat(decision.reason()).isEqualTo(InterviewSupervisorReason.FOLLOW_UP_RECOMMENDED);
    }

    @Test
    void shouldRejectEvidenceReviewOutsideCurrentQuestionScope() {
        when(evidenceReview.questionId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.superviseRound(
                INTERVIEW_ID,
                1,
                QUESTION_ID,
                ANSWER_ID,
                TECHNICAL_REVIEW_ID,
                EVIDENCE_REVIEW_ID
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("作用域不一致");
    }

    private void stubCompleteFacts() {
        MockInterviewSession session = mock(MockInterviewSession.class);
        InterviewRound round = mock(InterviewRound.class);
        InterviewQuestion question = mock(InterviewQuestion.class);
        InterviewAnswer answer = mock(InterviewAnswer.class);
        TechnicalReview technicalReview = mock(TechnicalReview.class);
        evidenceReview = mock(EvidenceReview.class);

        when(sessionRepository.findById(OWNER, INTERVIEW_ID)).thenReturn(Optional.of(session));
        when(session.ownerId()).thenReturn(OWNER);
        when(session.interviewId()).thenReturn(INTERVIEW_ID);
        when(session.status()).thenReturn(InterviewStatus.REVIEWING);
        when(session.budgetPolicy()).thenReturn(new InterviewBudgetPolicy(5, 2, 12, 12_000));

        when(roundRepository.findRoundByNumber(OWNER, INTERVIEW_ID, 1)).thenReturn(Optional.of(round));
        when(round.roundId()).thenReturn(ROUND_ID);
        when(round.ownerId()).thenReturn(OWNER);
        when(round.interviewId()).thenReturn(INTERVIEW_ID);
        when(round.roundNo()).thenReturn(1);
        when(round.status()).thenReturn(InterviewRoundStatus.ANSWERED);

        when(roundRepository.findQuestionByRound(OWNER, INTERVIEW_ID, ROUND_ID)).thenReturn(Optional.of(question));
        when(question.questionId()).thenReturn(QUESTION_ID);
        when(question.roundId()).thenReturn(ROUND_ID);
        when(question.interviewId()).thenReturn(INTERVIEW_ID);
        when(question.ownerId()).thenReturn(OWNER);
        when(question.followUpAllowed()).thenReturn(true);

        when(roundRepository.findAnswerByQuestion(OWNER, INTERVIEW_ID, QUESTION_ID)).thenReturn(Optional.of(answer));
        when(answer.answerId()).thenReturn(ANSWER_ID);
        when(answer.questionId()).thenReturn(QUESTION_ID);
        when(answer.roundId()).thenReturn(ROUND_ID);
        when(answer.interviewId()).thenReturn(INTERVIEW_ID);
        when(answer.ownerId()).thenReturn(OWNER);

        when(reviewRepository.findTechnicalReviewById(OWNER, INTERVIEW_ID, TECHNICAL_REVIEW_ID))
                .thenReturn(Optional.of(technicalReview));
        when(technicalReview.ownerId()).thenReturn(OWNER);
        when(technicalReview.interviewId()).thenReturn(INTERVIEW_ID);
        when(technicalReview.roundId()).thenReturn(ROUND_ID);
        when(technicalReview.questionId()).thenReturn(QUESTION_ID);
        when(technicalReview.answerId()).thenReturn(ANSWER_ID);
        when(technicalReview.suggestedFollowUp()).thenReturn("请继续说明CAS失败后的重试策略。");

        when(reviewRepository.findEvidenceReviewById(OWNER, INTERVIEW_ID, EVIDENCE_REVIEW_ID))
                .thenReturn(Optional.of(evidenceReview));
        when(evidenceReview.ownerId()).thenReturn(OWNER);
        when(evidenceReview.interviewId()).thenReturn(INTERVIEW_ID);
        when(evidenceReview.roundId()).thenReturn(ROUND_ID);
        when(evidenceReview.questionId()).thenReturn(QUESTION_ID);
        when(evidenceReview.answerId()).thenReturn(ANSWER_ID);
        when(evidenceReview.source()).thenReturn(EvidenceReviewSource.MODEL);
        when(evidenceReview.verdict()).thenReturn(EvidenceConsistencyVerdict.SUPPORTED);
    }
}