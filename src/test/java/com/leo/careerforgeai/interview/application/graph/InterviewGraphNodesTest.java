package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.question.InterviewQuestionGenerationService;
import com.leo.careerforgeai.interview.application.snapshot.MockInterviewInputConflictException;
import com.leo.careerforgeai.interview.domain.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewRouteDecision;
import com.leo.careerforgeai.interview.domain.InterviewWaitReason;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.bsc.langgraph4j.state.AgentState;
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
 * @description: 验证Graph节点重读冻结事实并为首题和后续问题写入最小流程状态
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
class InterviewGraphNodesTest {

    private static final ActorId OWNER = new ActorId("graph-owner");
    private static final UUID INTERVIEW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FIRST_QUESTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SECOND_QUESTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String SNAPSHOT_HASH = "a".repeat(64);
    private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(30);
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");

    private MockInterviewSessionRepository sessionRepository;
    private InterviewQuestionGenerationService questionGenerationService;
    private InterviewGraphNodes nodes;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(MockInterviewSessionRepository.class);
        questionGenerationService = mock(InterviewQuestionGenerationService.class);
        nodes = new InterviewGraphNodes(
                () -> OWNER,
                sessionRepository,
                mock(InterviewRoundRepository.class),
                questionGenerationService,
                MODEL_TIMEOUT
        );
    }

    @Test
    void shouldLoadFrozenContextAndWriteOnlyFirstQuestionProgress() {
        InterviewGraphState initial = initialState(SNAPSHOT_HASH);
        InterviewQuestion question = question(FIRST_QUESTION_ID);

        when(sessionRepository.findById(OWNER, INTERVIEW_ID)).thenReturn(Optional.of(session(SNAPSHOT_HASH)));
        when(questionGenerationService.generateAndPersistQuestion(INTERVIEW_ID, 1, null, MODEL_TIMEOUT))
                .thenReturn(question);

        assertThat(nodes.loadFrozenContext(initial)).isEmpty();
        InterviewGraphState waiting = apply(initial, nodes.generateAndPersistQuestion(initial));

        assertThat(waiting.currentRound()).isEqualTo(1);
        assertThat(waiting.currentQuestionId()).contains(FIRST_QUESTION_ID);
        assertThat(waiting.waitReason()).contains(InterviewWaitReason.WAITING_FOR_ANSWER);
        assertThat(waiting.data()).doesNotContainKeys("question", "questionDraft", "resumeContent");
        verify(questionGenerationService).generateAndPersistQuestion(INTERVIEW_ID, 1, null, MODEL_TIMEOUT);
    }

    @Test
    void shouldUseSupervisorRouteForSecondQuestionAndClearItAfterPersistence() {
        Map<String, Object> data = new HashMap<>(initialState(SNAPSHOT_HASH).data());
        data.put(InterviewGraphState.CURRENT_ROUND, 1);
        data.put(InterviewGraphState.CURRENT_QUESTION_ID, FIRST_QUESTION_ID.toString());
        data.put(InterviewGraphState.ROUTE_DECISION, InterviewRouteDecision.FOLLOW_UP.name());
        InterviewGraphState current = new InterviewGraphState(data);
        InterviewQuestion question = question(SECOND_QUESTION_ID);

        when(questionGenerationService.generateAndPersistQuestion(
                INTERVIEW_ID, 2, InterviewRouteDecision.FOLLOW_UP, MODEL_TIMEOUT
        )).thenReturn(question);

        InterviewGraphState waiting = apply(current, nodes.generateAndPersistQuestion(current));

        assertThat(waiting.currentRound()).isEqualTo(2);
        assertThat(waiting.currentQuestionId()).contains(SECOND_QUESTION_ID);
        assertThat(waiting.waitReason()).contains(InterviewWaitReason.WAITING_FOR_ANSWER);
        assertThat(waiting.routeDecision()).isEmpty();
        verify(questionGenerationService).generateAndPersistQuestion(
                INTERVIEW_ID, 2, InterviewRouteDecision.FOLLOW_UP, MODEL_TIMEOUT
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

    private InterviewGraphState apply(InterviewGraphState state, Map<String, Object> update) {
        Map<String, Object> merged = new HashMap<>(state.data());
        update.forEach((key, value) -> {
            if (AgentState.MARK_FOR_REMOVAL.equals(value)) {
                merged.remove(key);
            } else {
                merged.put(key, value);
            }
        });
        return new InterviewGraphState(merged);
    }

    private InterviewGraphState initialState(String snapshotHash) {
        return new InterviewGraphState(
                InterviewGraphState.initialData(INTERVIEW_ID, InterviewMode.TARGETED_MOCK, snapshotHash)
        );
    }

    private MockInterviewSession session(String snapshotHash) {
        return MockInterviewSession.create(
                INTERVIEW_ID,
                OWNER,
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                "c".repeat(64),
                InterviewMode.TARGETED_MOCK,
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                snapshotHash,
                new InterviewBudgetPolicy(3, 1, 12, 12_000),
                NOW
        );
    }

    private InterviewQuestion question(UUID questionId) {
        InterviewQuestion question = mock(InterviewQuestion.class);
        when(question.interviewId()).thenReturn(INTERVIEW_ID);
        when(question.questionId()).thenReturn(questionId);
        return question;
    }
}