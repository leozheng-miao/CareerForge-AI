package com.leo.careerforgeai.interview.application.supervision;

import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.domain.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewRoundStatus;
import com.leo.careerforgeai.interview.domain.InterviewRouteDecision;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证Supervisor路由原子推进回合与面试状态并支持提交后Checkpoint重放
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
class InterviewRouteApplicationServiceTest {

    private static final ActorId OWNER = new ActorId("route-owner");
    private static final UUID INTERVIEW_ID = UUID.randomUUID();
    private static final UUID ROUND_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    private MockInterviewSessionRepository sessionRepository;
    private InterviewRoundRepository roundRepository;
    private InterviewRouteApplicationService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(MockInterviewSessionRepository.class);
        roundRepository = mock(InterviewRoundRepository.class);
        service = new InterviewRouteApplicationService(
                () -> OWNER,
                sessionRepository,
                roundRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldReviewRoundAndContinueQuestioningAtomically() {
        MockInterviewSession session = reviewingSession();
        InterviewRound round = answeredRound();
        when(sessionRepository.findById(OWNER, INTERVIEW_ID)).thenReturn(Optional.of(session));
        when(roundRepository.findRoundByNumber(OWNER, INTERVIEW_ID, 1)).thenReturn(Optional.of(round));
        when(roundRepository.updateRoundIfVersionMatches(eq(OWNER), org.mockito.ArgumentMatchers.any(), eq(round.version())))
                .thenReturn(true);
        when(sessionRepository.updateIfVersionMatches(eq(OWNER), org.mockito.ArgumentMatchers.any(), eq(session.version())))
                .thenReturn(true);

        MockInterviewSession result = service.apply(
                INTERVIEW_ID,
                1,
                InterviewRouteDecision.NEXT_QUESTION,
                null
        );

        ArgumentCaptor<InterviewRound> roundCaptor = ArgumentCaptor.forClass(InterviewRound.class);
        ArgumentCaptor<MockInterviewSession> sessionCaptor = ArgumentCaptor.forClass(MockInterviewSession.class);
        verify(roundRepository).updateRoundIfVersionMatches(
                eq(OWNER),
                roundCaptor.capture(),
                eq(round.version())
        );
        verify(sessionRepository).updateIfVersionMatches(
                eq(OWNER),
                sessionCaptor.capture(),
                eq(session.version())
        );
        assertThat(roundCaptor.getValue().status()).isEqualTo(InterviewRoundStatus.REVIEWED);
        assertThat(sessionCaptor.getValue().status()).isEqualTo(InterviewStatus.GENERATING_QUESTION);
        assertThat(result.status()).isEqualTo(InterviewStatus.GENERATING_QUESTION);
    }

    @Test
    void shouldReplayAlreadyAppliedRouteWithoutWritingAgain() {
        MockInterviewSession routed = reviewingSession().continueQuestioning(NOW);
        InterviewRound reviewed = answeredRound().review(NOW);
        when(sessionRepository.findById(OWNER, INTERVIEW_ID)).thenReturn(Optional.of(routed));
        when(roundRepository.findRoundByNumber(OWNER, INTERVIEW_ID, 1)).thenReturn(Optional.of(reviewed));

        MockInterviewSession result = service.apply(
                INTERVIEW_ID,
                1,
                InterviewRouteDecision.NEXT_QUESTION,
                null
        );

        assertThat(result).isEqualTo(routed);
        verify(roundRepository, never()).updateRoundIfVersionMatches(
                eq(OWNER),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()
        );
        verify(sessionRepository, never()).updateIfVersionMatches(
                eq(OWNER),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    void shouldFinalizeFailureWithStableFailureCode() {
        MockInterviewSession session = reviewingSession();
        InterviewRound round = answeredRound();
        when(sessionRepository.findById(OWNER, INTERVIEW_ID)).thenReturn(Optional.of(session));
        when(roundRepository.findRoundByNumber(OWNER, INTERVIEW_ID, 1)).thenReturn(Optional.of(round));
        when(roundRepository.updateRoundIfVersionMatches(eq(OWNER), org.mockito.ArgumentMatchers.any(), eq(round.version())))
                .thenReturn(true);
        when(sessionRepository.updateIfVersionMatches(eq(OWNER), org.mockito.ArgumentMatchers.any(), eq(session.version())))
                .thenReturn(true);

        MockInterviewSession result = service.apply(
                INTERVIEW_ID,
                1,
                InterviewRouteDecision.FINALIZE_FAILURE,
                InterviewFailureCode.BUDGET_EXHAUSTED
        );

        assertThat(result.status()).isEqualTo(InterviewStatus.FAILED);
        assertThat(result.failureCode()).isEqualTo(InterviewFailureCode.BUDGET_EXHAUSTED);
    }

    private MockInterviewSession reviewingSession() {
        return MockInterviewSession.create(
                        INTERVIEW_ID,
                        OWNER,
                        UUID.randomUUID(),
                        "a".repeat(64),
                        InterviewMode.TARGETED_MOCK,
                        UUID.randomUUID(),
                        "b".repeat(64),
                        new InterviewBudgetPolicy(5, 2, 12, 12_000),
                        NOW
                )
                .startQuestionGeneration(NOW)
                .waitForAnswer(NOW)
                .startReview(NOW);
    }

    private InterviewRound answeredRound() {
        return InterviewRound.questionReady(ROUND_ID, INTERVIEW_ID, OWNER, 1, NOW).answer(NOW);
    }
}