package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.question.InterviewQuestionGenerationService;
import com.leo.careerforgeai.interview.application.snapshot.MockInterviewInputConflictException;
import com.leo.careerforgeai.interview.domain.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewWaitReason;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证Graph节点重读冻结事实并只向State写入首题ID和等待元数据
 * @author: Miao Zheng
 * @date: 2026-08-28
 **/
class InterviewGraphNodesTest {

    private static final ActorId OWNER = new ActorId("graph-owner");
    private static final UUID INTERVIEW_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID QUESTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String SNAPSHOT_HASH = "a".repeat(64);
    private static final Instant NOW =
            Instant.parse("2026-08-28T00:00:00Z");

    private MockInterviewSessionRepository sessionRepository;
    private InterviewQuestionGenerationService questionGenerationService;
    private InterviewGraphNodes nodes;
    private InterviewRoundRepository roundRepository;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(MockInterviewSessionRepository.class);
        questionGenerationService = mock(InterviewQuestionGenerationService.class);
        roundRepository = mock(InterviewRoundRepository.class);
        nodes = new InterviewGraphNodes(
                () -> OWNER,
                sessionRepository,
                roundRepository,
                questionGenerationService,
                Duration.ofSeconds(30)
        );
    }

    @Test
    void shouldLoadFrozenContextAndWriteOnlyQuestionProgressToState() {
        InterviewGraphState initial = initialState(SNAPSHOT_HASH);
        MockInterviewSession session = session(SNAPSHOT_HASH);
        InterviewQuestion question = mock(InterviewQuestion.class);

        when(sessionRepository.findById(OWNER, INTERVIEW_ID))
                .thenReturn(Optional.of(session));
        when(questionGenerationService.generateAndPersistFirstQuestion(
                INTERVIEW_ID,
                Duration.ofSeconds(30)
        )).thenReturn(question);
        when(question.interviewId()).thenReturn(INTERVIEW_ID);
        when(question.questionId()).thenReturn(QUESTION_ID);

        assertThat(nodes.loadFrozenContext(initial)).isEmpty();

        Map<String, Object> nextData = new HashMap<>(initial.data());
        nextData.putAll(nodes.generateAndPersistQuestion(initial));
        InterviewGraphState waiting = new InterviewGraphState(nextData);

        assertThat(waiting.currentRound()).isEqualTo(1);
        assertThat(waiting.currentQuestionId()).contains(QUESTION_ID);
        assertThat(waiting.waitReason())
                .contains(InterviewWaitReason.WAITING_FOR_ANSWER);
        assertThat(waiting.data())
                .doesNotContainKeys("question", "questionDraft", "resumeContent");

        verify(questionGenerationService).generateAndPersistFirstQuestion(
                INTERVIEW_ID,
                Duration.ofSeconds(30)
        );
    }

    @Test
    void shouldRejectCheckpointThatDoesNotMatchMysqlSnapshot() {
        InterviewGraphState state = initialState(SNAPSHOT_HASH);

        when(sessionRepository.findById(OWNER, INTERVIEW_ID))
                .thenReturn(Optional.of(session("b".repeat(64))));

        assertThatThrownBy(() -> nodes.loadFrozenContext(state))
                .isInstanceOf(MockInterviewInputConflictException.class);
    }

    private InterviewGraphState initialState(String snapshotHash) {
        return new InterviewGraphState(
                InterviewGraphState.initialData(
                        INTERVIEW_ID,
                        InterviewMode.TARGETED_MOCK,
                        snapshotHash
                )
        );
    }

    private MockInterviewSession session(String snapshotHash) {
        return MockInterviewSession.create(
                INTERVIEW_ID,
                OWNER,
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "c".repeat(64),
                InterviewMode.TARGETED_MOCK,
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                snapshotHash,
                new InterviewBudgetPolicy(3, 1, 12, 12_000),
                NOW
        );
    }
}