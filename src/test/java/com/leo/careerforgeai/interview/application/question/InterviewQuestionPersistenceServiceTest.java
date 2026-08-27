package com.leo.careerforgeai.interview.application.question;

import com.leo.careerforgeai.interview.application.model.contract.InterviewQuestionDraft;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.domain.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewQuestionType;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

/**
 * @program: CareerForge-AI
 * @description: 验证首题生成权、问题持久化和Session等待状态的幂等推进
 * @author: Miao Zheng
 * @date: 2026-08-28
 **/
class InterviewQuestionPersistenceServiceTest {

    private static final ActorId OWNER = new ActorId("question-owner");
    private static final UUID INTERVIEW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SNAPSHOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");

    @Test
    void shouldPersistFirstQuestionOnceAndReturnItOnReplay() {
        MockInterviewSessionRepository sessionRepository = mock(MockInterviewSessionRepository.class);
        InterviewRoundRepository roundRepository = mock(InterviewRoundRepository.class);
        MockInterviewSession created = session();
        MockInterviewSession generating = created.startQuestionGeneration(NOW);
        MockInterviewSession waiting = generating.waitForAnswer(NOW);
        AtomicReference<InterviewRound> storedRound = new AtomicReference<>();
        AtomicReference<InterviewQuestion> storedQuestion = new AtomicReference<>();

        when(sessionRepository.findById(OWNER, INTERVIEW_ID))
                .thenReturn(Optional.of(created), Optional.of(generating), Optional.of(waiting));
        when(sessionRepository.updateIfVersionMatches(eq(OWNER), any(), any(Long.class))).thenReturn(true);
        when(roundRepository.findRoundByNumber(OWNER, INTERVIEW_ID, 1))
                .thenReturn(Optional.empty(), Optional.empty())
                .thenAnswer(invocation -> Optional.of(storedRound.get()));
        when(roundRepository.claimQuestionReadyRound(any(), any())).thenAnswer(invocation -> {
            storedRound.set(invocation.getArgument(0));
            storedQuestion.set(invocation.getArgument(1));
            return storedQuestion.get();
        });
        when(roundRepository.findQuestionByRound(eq(OWNER), eq(INTERVIEW_ID), any()))
                .thenAnswer(invocation -> Optional.of(storedQuestion.get()));

        InterviewQuestionPersistenceService service = new InterviewQuestionPersistenceService(
                () -> OWNER,
                sessionRepository,
                roundRepository,
                new InterviewQuestionFactory(JsonMapper.builder().build()),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThat(service.startFirstQuestionGeneration(INTERVIEW_ID)).isEmpty();
        InterviewQuestion first = service.persistFirstQuestion(INTERVIEW_ID, result());
        Optional<InterviewQuestion> replay = service.startFirstQuestionGeneration(INTERVIEW_ID);

        assertThat(replay).contains(first);
        assertThat(first.roundId()).isEqualTo(storedRound.get().roundId());
        verify(roundRepository, times(1)).claimQuestionReadyRound(any(), any());

        ArgumentCaptor<MockInterviewSession> sessionCaptor =
                ArgumentCaptor.forClass(MockInterviewSession.class);
        verify(sessionRepository, times(2))
                .updateIfVersionMatches(eq(OWNER), sessionCaptor.capture(), any(Long.class));
        assertThat(sessionCaptor.getAllValues())
                .extracting(MockInterviewSession::status)
                .containsExactly(
                        InterviewStatus.GENERATING_QUESTION,
                        InterviewStatus.WAITING_FOR_ANSWER
                );
    }

    private MockInterviewSession session() {
        return MockInterviewSession.create(
                INTERVIEW_ID,
                OWNER,
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "a".repeat(64),
                InterviewMode.TARGETED_MOCK,
                SNAPSHOT_ID,
                "b".repeat(64),
                new InterviewBudgetPolicy(3, 1, 12, 12_000),
                NOW
        );
    }

    private InterviewRoleModelGateway.Result<InterviewQuestionDraft> result() {
        InterviewQuestionDraft draft = new InterviewQuestionDraft(
                InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                "解释Java锁的可重入性。",
                List.of("Java并发"),
                2,
                List.of("说明重入语义"),
                true,
                List.of()
        );
        return new InterviewRoleModelGateway.Result<>(
                draft,
                "request-1",
                "deepseek-chat",
                "interviewer-v1",
                new ModelUsage(200, 80, 280),
                1000,
                1,
                false,
                "c".repeat(64)
        );
    }
}