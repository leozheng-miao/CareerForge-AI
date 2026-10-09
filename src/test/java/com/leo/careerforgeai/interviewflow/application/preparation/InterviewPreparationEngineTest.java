package com.leo.careerforgeai.interviewflow.application.preparation;

import com.leo.careerforgeai.interviewflow.application.port.InterviewInputSnapshotRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewModelPort;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.KnowledgeQuestionCandidate;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.SourceReference;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionRepository.QuestionWrite;
import com.leo.careerforgeai.interviewflow.application.port.InterviewSessionRepository;
import com.leo.careerforgeai.interviewflow.application.port.InterviewTopicRepository;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.QuickInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.session.SessionStage;
import com.leo.careerforgeai.interviewflow.domain.turn.QuestionStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证快速面试准备的题源、去重、回退和READY发布门槛
 * @author: Miao Zheng
 * @date: 2026-10-06
 */
@ExtendWith(MockitoExtension.class)
class InterviewPreparationEngineTest {

    private static final ActorId OWNER = new ActorId("quick-owner-a");
    private static final UUID INTERVIEW_ID = UUID.fromString(
            "42000000-0000-0000-0000-000000000001"
    );
    private static final Instant NOW = Instant.parse(
            "2026-10-06T08:00:00Z"
    );
    private static final SourceReference RAG_SOURCE = new SourceReference(
            "careerforge-career-materials",
            "ai-interview-summary",
            "a".repeat(64)
    );

    @Mock
    private InterviewSessionRepository sessionRepository;

    @Mock
    private InterviewInputSnapshotRepository inputSnapshotRepository;

    @Mock
    private InterviewTopicRepository topicRepository;

    @Mock
    private InterviewQuestionRepository questionRepository;

    private InterviewAggregate preparing;

    @BeforeEach
    void setUp() {
        preparing = InterviewAggregate.createQuick(
                INTERVIEW_ID,
                OWNER,
                NOW,
                new QuickInterviewPolicy()
        );
        lenient().when(sessionRepository.findById(OWNER, INTERVIEW_ID))
                .thenReturn(Optional.of(preparing));
        lenient().when(sessionRepository.findVersions(OWNER, INTERVIEW_ID))
                .thenReturn(Optional.of(
                        new InterviewSessionRepository.SessionVersions(1, 1)
                ));
        lenient().when(inputSnapshotRepository.findBySession(
                        OWNER,
                        INTERVIEW_ID
                ))
                .thenReturn(Optional.of(inputSnapshot()));
        lenient().when(topicRepository.findTopics(OWNER, INTERVIEW_ID))
                .thenReturn(List.of());
        lenient().when(questionRepository.findQuestions(
                        OWNER,
                        INTERVIEW_ID
                ))
                .thenReturn(List.of());
        lenient().when(sessionRepository.updateIfVersionMatches(
                        any(),
                        any(),
                        anyLong()
                ))
                .thenReturn(true);
    }

    @Test
    void shouldPersistEightSourceBearingQuestionsBeforePublishingReady() {
        InterviewQuestionKnowledgePort knowledge = (direction, objective, limit) ->
                List.of(new KnowledgeQuestionCandidate(
                        "公共面经片段：" + objective,
                        List.of(RAG_SOURCE)
                ));
        InterviewModelPort model = request -> Optional.of(
                new InterviewModelPort.GeneratedQuestion(
                        "针对“" + request.objective()
                                + "”，请结合一个具体系统说明你的设计取舍和故障处理方式。",
                        List.of(RAG_SOURCE)
                )
        );
        InterviewPreparationEngine engine = engine(knowledge, model);

        var result = engine.prepareQuick(INTERVIEW_ID);

        ArgumentCaptor<List> topicCaptor = ArgumentCaptor.forClass(List.class);
        verify(topicRepository).insertAll(
                any(),
                any(),
                anyLong(),
                topicCaptor.capture()
        );
        ArgumentCaptor<List> questionCaptor = ArgumentCaptor.forClass(
                List.class
        );
        verify(questionRepository).insertAll(
                any(),
                any(),
                questionCaptor.capture()
        );
        @SuppressWarnings("unchecked")
        List<QuestionWrite> questions = questionCaptor.getValue();

        assertThat(topicCaptor.getValue()).hasSize(8);
        assertThat(questions).hasSize(8);
        assertThat(questions.getFirst().question().status())
                .isEqualTo(QuestionStatus.PRESENTED);
        assertThat(questions.subList(1, questions.size()))
                .allMatch(write -> write.question().status()
                        == QuestionStatus.RESERVED);
        assertThat(questions)
                .extracting(QuestionWrite::contentFingerprint)
                .doesNotHaveDuplicates();
        assertThat(questions)
                .allMatch(write -> write.sourceRefsJson()
                        .contains(RAG_SOURCE.chunkId()));
        assertThat(result.stage()).isEqualTo(SessionStage.READY);
        assertThat(result.preparedQuestionCount()).isEqualTo(8);

        ArgumentCaptor<InterviewAggregate> sessionCaptor =
                ArgumentCaptor.forClass(InterviewAggregate.class);
        verify(sessionRepository).updateIfVersionMatches(
                any(),
                sessionCaptor.capture(),
                anyLong()
        );
        assertThat(sessionCaptor.getValue().session().stage())
                .isEqualTo(SessionStage.READY);
    }

    @Test
    void shouldRejectDuplicateGeneratedContentAndUseDistinctBackups() {
        InterviewQuestionKnowledgePort knowledge = (direction, objective, limit) ->
                List.of(new KnowledgeQuestionCandidate(
                        "同一候选片段",
                        List.of(RAG_SOURCE)
                ));
        String duplicateQuestion =
                "请完整说明一个 Agent 系统的设计、实现、异常处理和验证方法。";
        InterviewModelPort model = request -> Optional.of(
                new InterviewModelPort.GeneratedQuestion(
                        duplicateQuestion,
                        List.of(RAG_SOURCE)
                )
        );

        engine(knowledge, model).prepareQuick(INTERVIEW_ID);

        ArgumentCaptor<List> questionCaptor = ArgumentCaptor.forClass(
                List.class
        );
        verify(questionRepository).insertAll(
                any(),
                any(),
                questionCaptor.capture()
        );
        @SuppressWarnings("unchecked")
        List<QuestionWrite> questions = questionCaptor.getValue();
        assertThat(questions)
                .extracting(QuestionWrite::contentFingerprint)
                .doesNotHaveDuplicates();
        assertThat(questions)
                .extracting(write -> write.question().content())
                .filteredOn(duplicateQuestion::equals)
                .hasSize(1);
        assertThat(questions.subList(1, questions.size()))
                .allMatch(write -> write.sourceRefsJson()
                        .contains("careerforge-admin-policy"));
    }

    @Test
    void shouldUseAdministratorBackupCorpusWhenRetrievalFails() {
        InterviewQuestionKnowledgePort unavailableKnowledge =
                (direction, objective, limit) -> {
                    throw new IllegalStateException("检索不可用");
                };
        InterviewModelPort modelMustNotRun = request -> {
            throw new AssertionError("无题库候选时不应调用模型");
        };

        var result = engine(
                unavailableKnowledge,
                modelMustNotRun
        ).prepareQuick(INTERVIEW_ID);

        ArgumentCaptor<List> questionCaptor = ArgumentCaptor.forClass(
                List.class
        );
        verify(questionRepository).insertAll(
                any(),
                any(),
                questionCaptor.capture()
        );
        @SuppressWarnings("unchecked")
        List<QuestionWrite> questions = questionCaptor.getValue();
        assertThat(questions).hasSize(8);
        assertThat(questions)
                .allMatch(write -> write.sourceRefsJson()
                        .contains("careerforge-admin-policy"));
        assertThat(result.stage()).isEqualTo(SessionStage.READY);
    }

    @Test
    void shouldUseAdministratorBackupCorpusWhenModelFails() {
        InterviewQuestionKnowledgePort knowledge = (direction, objective, limit) ->
                List.of(new KnowledgeQuestionCandidate(
                        "公共面经片段：" + objective,
                        List.of(RAG_SOURCE)
                ));
        InterviewModelPort unavailableModel = request -> {
            throw new IllegalStateException("模型不可用");
        };

        var result = engine(
                knowledge,
                unavailableModel
        ).prepareQuick(INTERVIEW_ID);

        ArgumentCaptor<List> questionCaptor = ArgumentCaptor.forClass(
                List.class
        );
        verify(questionRepository).insertAll(
                any(),
                any(),
                questionCaptor.capture()
        );
        @SuppressWarnings("unchecked")
        List<QuestionWrite> questions = questionCaptor.getValue();
        assertThat(questions).hasSize(8);
        assertThat(questions)
                .allMatch(write -> write.sourceRefsJson()
                        .contains("careerforge-admin-policy"));
        assertThat(result.stage()).isEqualTo(SessionStage.READY);
    }

    @Test
    void shouldNotPublishReadyWhenQualifiedQuestionPoolCannotPersist() {
        InterviewQuestionKnowledgePort knowledge = (direction, objective, limit) ->
                List.of();
        InterviewModelPort model = request -> Optional.empty();
        doThrow(new IllegalStateException("题目持久化失败"))
                .when(questionRepository)
                .insertAll(any(), any(), any());

        assertThatThrownBy(() -> engine(knowledge, model)
                .prepareQuick(INTERVIEW_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("题目持久化失败");

        verify(sessionRepository, never()).updateIfVersionMatches(
                any(),
                any(),
                anyLong()
        );
    }

    @Test
    void shouldRejectStaleInputVersionBeforeWritingPreparation() {
        InterviewQuestionKnowledgePort knowledge = (direction, objective, limit) ->
                List.of();
        InterviewModelPort model = request -> Optional.empty();

        assertThatThrownBy(() -> engine(knowledge, model).prepareQuick(
                OWNER,
                INTERVIEW_ID,
                2,
                1
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("过期");

        verify(topicRepository, never()).insertAll(
                any(),
                any(),
                anyLong(),
                any()
        );
        verify(questionRepository, never()).insertAll(
                any(),
                any(),
                any()
        );
    }

    private InterviewPreparationEngine engine(
            InterviewQuestionKnowledgePort knowledge,
            InterviewModelPort model
    ) {
        return new InterviewPreparationEngine(
                () -> OWNER,
                sessionRepository,
                inputSnapshotRepository,
                topicRepository,
                questionRepository,
                knowledge,
                model,
                new QuickInterviewBlueprintPlanner(),
                JsonMapper.builder().build()
        );
    }

    private InterviewInputSnapshotRepository.InputSnapshot inputSnapshot() {
        return new InterviewInputSnapshotRepository.InputSnapshot(
                UUID.fromString(
                        "42000000-0000-0000-0000-000000000002"
                ),
                INTERVIEW_ID,
                OWNER,
                "AI_AGENT_DEVELOPMENT",
                null,
                null,
                null,
                null,
                null,
                null,
                3600,
                1,
                0,
                NOW,
                NOW
        );
    }
}
