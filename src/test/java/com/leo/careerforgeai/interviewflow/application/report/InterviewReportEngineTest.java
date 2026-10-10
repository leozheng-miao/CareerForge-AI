package com.leo.careerforgeai.interviewflow.application.report;

import com.leo.careerforgeai.interviewflow.application.report.InterviewEvidencePack.TopicEvidence;
import com.leo.careerforgeai.interviewflow.application.report.InterviewEvidencePack.TurnEvidence;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;

class InterviewReportEngineTest {
    private static final UUID INTERVIEW=UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID TOPIC=UUID.fromString("51000000-0000-0000-0000-000000000002");
    private static final UUID ANSWER=UUID.fromString("51000000-0000-0000-0000-000000000003");
    private static final UUID SKIPPED_TOPIC=UUID.fromString("51000000-0000-0000-0000-000000000004");
    private final RecordingModelGateway model=new RecordingModelGateway();
    private final InterviewReportEngine engine=new InterviewReportEngine(model, JsonMapper.builder().build());

    @Test void resolvesCitationReferenceToExactFrozenAnswerText() {
        model.respond(validJson());
        var result=engine.generate(evidence());
        assertThat(result.strengths()).singleElement().extracting(InterviewReportEngine.Finding::quote)
                .isEqualTo("我使用了幂等键和有界重试，并记录任务租约。");
    }
    @Test void rejectsNumericScore() {
        model.respond(validJson().replace("实现可靠", "95分"));
        model.respond(validJson().replace("实现可靠", "95分"));
        assertThatThrownBy(() -> engine.generate(evidence())).hasMessageContaining("评分");
        assertThat(model.callCount()).isEqualTo(2);
    }
    @Test void retriesOnceWhenModelReturnsNumericScore() {
        model.respond(validJson().replace("实现可靠", "95分"));
        model.respond(validJson());

        var result = engine.generate(evidence());

        assertThat(result.strengths()).singleElement()
                .extracting(InterviewReportEngine.Finding::title)
                .isEqualTo("实现可靠");
        assertThat(model.callCount()).isEqualTo(2);
    }
    @Test void allowsFactualDurationsAndPercentagesInsteadOfTreatingThemAsScores() {
        model.respond(validJson()
                .replace("回答聚焦可靠性", "将处理耗时从5分钟降至1分钟，失败率降低30%"));

        var result = engine.generate(evidence());

        assertThat(result.summary()).contains("5分钟", "30%");
    }
    @Test void rejectsUnknownCitationReference() {
        model.respond(validJson().replace(ANSWER+":1", ANSWER+":99"));
        assertThatThrownBy(() -> engine.generate(evidence())).hasMessageContaining("未知原文片段");
    }
    @Test void refusesZeroAnswerReport() {
        assertThatThrownBy(() -> engine.generate(new InterviewEvidencePack(INTERVIEW,
                List.of(new TopicEvidence(TOPIC,1,"可靠性",false,List.of())))))
                .hasMessageContaining("不得生成报告");
        assertThat(model.callCount()).isZero();
    }
    @Test void omitsUncoveredTopicsFromModelInputAndPublishedReport() {
        model.respond(jsonWithUncoveredTopicReview());

        var result=engine.generate(evidenceWithSkippedTopic());

        assertThat(result.topicReviews())
                .extracting(InterviewReportEngine.TopicReview::sequenceNo)
                .containsExactly(1);
        assertThat(model.lastTaskType()).isEqualTo(ModelTaskType.INTERVIEW_REPORT);
        assertThat(model.lastRequest().messages().getLast().content()).doesNotContain("未考察的消息队列主题");
    }
    private InterviewEvidencePack evidence(){return new InterviewEvidencePack(INTERVIEW,List.of(
            new TopicEvidence(TOPIC,1,"可靠性",true,List.of(new TurnEvidence(ANSWER,
                    UUID.randomUUID(),"如何保证重试安全？","我使用了幂等键和有界重试，并记录任务租约。","a".repeat(64),"{}")))));}
    private InterviewEvidencePack evidenceWithSkippedTopic(){return new InterviewEvidencePack(INTERVIEW,List.of(
            new TopicEvidence(TOPIC,1,"可靠性",true,List.of(new TurnEvidence(ANSWER,
                    UUID.randomUUID(),"如何保证重试安全？","我使用了幂等键和有界重试，并记录任务租约。","a".repeat(64),"{}"))),
            new TopicEvidence(SKIPPED_TOPIC,2,"未考察的消息队列主题",false,List.of())));}
    private String validJson(){return "{\"summary\":\"回答聚焦可靠性\",\"strengths\":[{\"title\":\"实现可靠\",\"description\":\"说明了关键机制\",\"citationId\":\""+ANSWER+":1\"}],\"improvements\":[],\"topicReviews\":[{\"sequenceNo\":1,\"conclusion\":\"有明确实现依据\",\"answerIds\":[\""+ANSWER+"\"]}]}";}
    private String jsonWithUncoveredTopicReview(){return "{\"summary\":\"回答聚焦可靠性\",\"strengths\":[{\"title\":\"实现可靠\",\"description\":\"说明了关键机制\",\"citationId\":\""+ANSWER+":1\"}],\"improvements\":[],\"topicReviews\":[{\"sequenceNo\":1,\"conclusion\":\"有明确实现依据\",\"answerIds\":[\""+ANSWER+"\"]},{\"sequenceNo\":2,\"conclusion\":\"未说明消息队列\",\"answerIds\":[]}]}";}
    private static final class RecordingModelGateway implements ModelGateway {
        private final ArrayDeque<String> responses = new ArrayDeque<>();
        private ModelTaskType lastTaskType;
        private ModelRequest lastRequest;
        private int callCount;

        void respond(String content) { responses.addLast(content); }
        int callCount() { return callCount; }
        ModelTaskType lastTaskType() { return lastTaskType; }
        ModelRequest lastRequest() { return lastRequest; }

        @Override public ModelResponse chat(ModelTaskType taskType, ModelRequest request) {
            callCount++;
            lastTaskType=taskType;
            lastRequest=request;
            return new ModelResponse("r","m",responses.removeFirst(),null);
        }

        @Override public void stream(ModelTaskType taskType, ModelRequest request,
                                     Consumer<ModelStreamEvent> eventConsumer) {
            throw new AssertionError("报告引擎不应调用流式模型接口");
        }
    }
}
