package com.leo.careerforgeai.interview.application.answer;

import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.domain.round.InterviewAnswer;
import com.leo.careerforgeai.interview.domain.session.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.session.InterviewMode;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.round.InterviewRound;
import com.leo.careerforgeai.interview.domain.round.InterviewRoundStatus;
import com.leo.careerforgeai.interview.domain.session.InterviewStatus;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyInt;

/**
 * @program: CareerForge-AI
 * @description: 验证答案、回合和Session原子推进及相同requestId幂等重放
 * @author: Miao Zheng
 * @date: 2026-08-28
 **/
class InterviewAnswerSubmissionServiceTest {

    private static final ActorId OWNER = new ActorId("answer-owner");
    private static final UUID INTERVIEW_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ROUND_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID QUESTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID REQUEST_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final Instant NOW =
            Instant.parse("2026-08-28T00:00:00Z");

    @Test
    void shouldSubmitOnceAndReturnSameAnswerForRequestReplay() {
        MockInterviewSessionRepository sessionRepository =
                mock(MockInterviewSessionRepository.class);
        InterviewRoundRepository roundRepository =
                mock(InterviewRoundRepository.class);
        MockInterviewSession waiting = waitingSession();
        InterviewRound round = InterviewRound.questionReady(
                ROUND_ID,
                INTERVIEW_ID,
                OWNER,
                1,
                NOW
        );
        InterviewQuestion question = mock(InterviewQuestion.class);
        AtomicReference<InterviewAnswer> storedAnswer = new AtomicReference<>();

        when(roundRepository.findAnswerByRequest(OWNER, REQUEST_ID))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation ->
                        Optional.of(storedAnswer.get()));
        when(sessionRepository.findById(OWNER, INTERVIEW_ID))
                .thenReturn(Optional.of(waiting));
        when(roundRepository.findRoundByNumber(OWNER, INTERVIEW_ID, 1))
                .thenReturn(Optional.of(round));
        when(roundRepository.findQuestionByRound(
                OWNER,
                INTERVIEW_ID,
                ROUND_ID
        )).thenReturn(Optional.of(question));
        when(question.questionId()).thenReturn(QUESTION_ID);
        when(question.interviewId()).thenReturn(INTERVIEW_ID);
        when(question.roundId()).thenReturn(ROUND_ID);
        when(question.ownerId()).thenReturn(OWNER);
        when(roundRepository.findAnswerByQuestion(
                OWNER,
                INTERVIEW_ID,
                QUESTION_ID
        )).thenReturn(Optional.empty());
        when(roundRepository.claimAnswer(any())).thenAnswer(invocation -> {
            InterviewAnswer answer = invocation.getArgument(0);
            storedAnswer.set(answer);
            return answer;
        });
        when(sessionRepository.updateIfVersionMatches(
                eq(OWNER),
                any(),
                eq(2L)
        )).thenReturn(true);
        when(roundRepository.updateRoundIfVersionMatches(
                eq(OWNER),
                any(),
                eq(0L)
        )).thenReturn(true);

        InterviewAnswerSubmissionService service =
                new InterviewAnswerSubmissionService(
                        () -> OWNER,
                        sessionRepository,
                        roundRepository,
                        JsonMapper.builder().build(),
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );

        InterviewAnswer first = service.submit(
                INTERVIEW_ID,
                1,
                QUESTION_ID,
                REQUEST_ID,
                2,
                "虚拟线程适合大量阻塞任务，但不会提高CPU密集计算速度。"
        );
        InterviewAnswer replay = service.submit(
                INTERVIEW_ID,
                1,
                QUESTION_ID,
                REQUEST_ID,
                2,
                "虚拟线程适合大量阻塞任务，但不会提高CPU密集计算速度。"
        );

        assertThat(replay).isSameAs(first);
        assertThat(first.answerText()).contains("大量阻塞任务");
        verify(roundRepository, times(1)).claimAnswer(any());

        ArgumentCaptor<MockInterviewSession> sessionCaptor =
                ArgumentCaptor.forClass(MockInterviewSession.class);
        verify(sessionRepository).updateIfVersionMatches(
                eq(OWNER),
                sessionCaptor.capture(),
                eq(2L)
        );
        assertThat(sessionCaptor.getValue().status())
                .isEqualTo(InterviewStatus.REVIEWING);
        assertThat(sessionCaptor.getValue().version()).isEqualTo(3);

        ArgumentCaptor<InterviewRound> roundCaptor =
                ArgumentCaptor.forClass(InterviewRound.class);
        verify(roundRepository).updateRoundIfVersionMatches(
                eq(OWNER),
                roundCaptor.capture(),
                eq(0L)
        );
        assertThat(roundCaptor.getValue().status())
                .isEqualTo(InterviewRoundStatus.ANSWERED);
        assertThat(roundCaptor.getValue().version()).isEqualTo(1);
    }

    @Test
    void shouldRejectOtherOwnerBeforeReadingQuestionOrWritingAnswer() {
        ActorId otherOwner = new ActorId("other-owner");
        MockInterviewSessionRepository sessionRepository = mock(MockInterviewSessionRepository.class);
        InterviewRoundRepository roundRepository = mock(InterviewRoundRepository.class);

        when(roundRepository.findAnswerByRequest(otherOwner, REQUEST_ID)).thenReturn(Optional.empty());
        when(sessionRepository.findById(otherOwner, INTERVIEW_ID)).thenReturn(Optional.empty());

        InterviewAnswerSubmissionService service = new InterviewAnswerSubmissionService(
                () -> otherOwner,
                sessionRepository,
                roundRepository,
                JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.submit(
                INTERVIEW_ID,
                1,
                QUESTION_ID,
                REQUEST_ID,
                2,
                "不能提交其他用户面试的答案。"
        )).isInstanceOf(MockInterviewNotFoundException.class);

        verify(roundRepository, never()).findRoundByNumber(any(), any(), anyInt());
        verify(roundRepository, never()).claimAnswer(any());
        verify(roundRepository, never()).updateRoundIfVersionMatches(any(), any(), anyLong());
        verify(sessionRepository, never()).updateIfVersionMatches(any(), any(), anyLong());
    }

    private MockInterviewSession waitingSession() {
        MockInterviewSession created = MockInterviewSession.create(
                INTERVIEW_ID,
                OWNER,
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000005"
                ),
                "a".repeat(64),
                InterviewMode.TARGETED_MOCK,
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000006"
                ),
                "b".repeat(64),
                new InterviewBudgetPolicy(3, 1, 12, 12_000),
                NOW
        );
        return created.startQuestionGeneration(NOW).waitForAnswer(NOW);
    }
}