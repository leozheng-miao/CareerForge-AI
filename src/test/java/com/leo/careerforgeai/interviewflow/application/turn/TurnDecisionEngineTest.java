package com.leo.careerforgeai.interviewflow.application.turn;

import com.leo.careerforgeai.interviewflow.application.context.ContextPurpose;
import com.leo.careerforgeai.interviewflow.application.context.InterviewModelContext;
import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository.AnswerRecord;
import com.leo.careerforgeai.interviewflow.application.port.InterviewAnswerRepository.FinalDecision;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.KnowledgeQuestionCandidate;
import com.leo.careerforgeai.interviewflow.application.port.InterviewQuestionKnowledgePort.SourceReference;
import com.leo.careerforgeai.interviewflow.application.tool.InterviewToolCatalog;
import com.leo.careerforgeai.interviewflow.application.tool.SearchInterviewQuestionsTool;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewAggregate;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewEvent;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewStateMachine;
import com.leo.careerforgeai.interviewflow.domain.session.QuickInterviewPolicy;
import com.leo.careerforgeai.interviewflow.domain.turn.InterviewAnswer;
import com.leo.careerforgeai.knowledge.config.KnowledgeSourceProperties;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TurnDecisionEngineTest {

    private static final ActorId OWNER = new ActorId("owner");
    private static final UUID INTERVIEW_ID = UUID.fromString(
            "44000000-0000-0000-0000-000000000001");
    private static final UUID TOPIC_ID = UUID.fromString(
            "44000000-0000-0000-0000-000000000002");
    private static final UUID QUESTION_ID = UUID.fromString(
            "44000000-0000-0000-0000-000000000003");
    private static final UUID ANSWER_ID = UUID.fromString(
            "44000000-0000-0000-0000-000000000004");
    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    @Test
    void shouldBoundReactToTwoModelCallsAndOneReadOnlyToolCall() {
        SourceReference source = new SourceReference(
                "kb", "doc", "a".repeat(64));
        ModelGateway model = mock(ModelGateway.class);
        when(model.chat(any(), any())).thenReturn(
                response("{\"decision\":\"NEXT_TOPIC\",\"analysis\":\"需要补充题库依据\",\"question\":null,\"citedChunkIds\":[],\"searchRequired\":true}"),
                response("{\"decision\":\"FOLLOW_UP\",\"analysis\":\"需要深入失败恢复\",\"question\":\"如果模型持续超时，你会如何保证已提交答案不丢失并安全进入下一步？\",\"citedChunkIds\":[\""
                        + source.chunkId()
                        + "\"],\"searchRequired\":false}")
        );
        SearchInterviewQuestionsTool tool = new SearchInterviewQuestionsTool(
                (direction, objective, limit) -> List.of(
                        new KnowledgeQuestionCandidate("题库片段", List.of(source)))
        );
        TurnDecisionEngine engine = new TurnDecisionEngine(
                model,
                JsonMapper.builder().build(),
                new InterviewToolCatalog(tool),
                new TurnDecisionPolicy(),
                new KnowledgeSourceProperties("kb", Path.of("."), List.of(
                        new KnowledgeSourceProperties.DocumentDefinition(
                                "doc", "doc", com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType.INTERVIEW_EXPERIENCE, "doc.md"))),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = engine.decide(new TurnDecisionEngine.TurnDecisionInput(
                context(), pausedAggregate(), submittedAnswer(), true));

        assertThat(result.decision()).isEqualTo(FinalDecision.FOLLOW_UP);
        assertThat(result.modelCallCount()).isEqualTo(2);
        assertThat(result.toolCallCount()).isEqualTo(1);
        assertThat(result.sourceReferences()).containsExactly(source);
    }

    @Test
    void shouldConservativelyChooseNextTopicForInvalidModelOutput() {
        ModelGateway model = mock(ModelGateway.class);
        when(model.chat(any(), any())).thenReturn(response("not-json"));
        SearchInterviewQuestionsTool tool = new SearchInterviewQuestionsTool(
                (direction, objective, limit) -> List.of());
        TurnDecisionEngine engine = new TurnDecisionEngine(
                model,
                JsonMapper.builder().build(),
                new InterviewToolCatalog(tool),
                new TurnDecisionPolicy(),
                new KnowledgeSourceProperties("kb", Path.of("."), List.of(
                        new KnowledgeSourceProperties.DocumentDefinition(
                                "doc", "doc", com.leo.careerforgeai.knowledge.domain.document.KnowledgeDocumentType.INTERVIEW_EXPERIENCE, "doc.md"))),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThat(engine.decide(new TurnDecisionEngine.TurnDecisionInput(
                context(), pausedAggregate(), submittedAnswer(), true
        )).decision()).isEqualTo(FinalDecision.NEXT_TOPIC);
    }

    private static ModelResponse response(String content) {
        return new ModelResponse("request", "model", content, null);
    }

    private static InterviewModelContext context() {
        return new InterviewModelContext(
                ContextPurpose.TURN_DECISION,
                INTERVIEW_ID,
                OWNER,
                "AI_AGENT_DEVELOPMENT",
                1,
                1,
                new InterviewModelContext.ActiveTopicContext(
                        TOPIC_ID,
                        1,
                        "可靠性",
                        List.of(new InterviewModelContext.TurnContext(
                                QUESTION_ID,
                                "如何设计模型重试？",
                                List.of("使用有限重试"),
                                "b".repeat(64),
                                "[{\"knowledgeBaseId\":\"kb\",\"documentId\":\"doc\",\"chunkId\":\""
                                        + "a".repeat(64)
                                        + "\",\"sourceHash\":null,\"startOffset\":null,\"endOffset\":null}]",
                                null
                        ))
                ),
                List.of()
        );
    }

    private static InterviewAggregate pausedAggregate() {
        InterviewStateMachine machine = new InterviewStateMachine(
                new QuickInterviewPolicy());
        InterviewAggregate aggregate = InterviewAggregate.createQuick(
                INTERVIEW_ID, OWNER, NOW, new QuickInterviewPolicy());
        aggregate = machine.transition(
                aggregate, InterviewEvent.PREPARATION_COMPLETED, NOW).aggregate();
        aggregate = machine.transition(
                aggregate, InterviewEvent.FORMAL_START_REQUESTED, NOW).aggregate();
        return machine.transition(
                aggregate, InterviewEvent.ANSWER_SUBMITTED, NOW).aggregate();
    }

    private static AnswerRecord submittedAnswer() {
        InterviewAnswer answer = InterviewAnswer.draft(
                ANSWER_ID, QUESTION_ID).saveDraft("使用有限重试").submit();
        return new AnswerRecord(
                answer,
                UUID.randomUUID(),
                "c".repeat(64),
                "b".repeat(64),
                null,
                null,
                null,
                NOW,
                0
        );
    }
}
