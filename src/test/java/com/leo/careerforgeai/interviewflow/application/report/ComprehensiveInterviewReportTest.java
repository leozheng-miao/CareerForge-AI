package com.leo.careerforgeai.interviewflow.application.report;

import com.leo.careerforgeai.interviewflow.application.report.InterviewEvidencePack.TopicEvidence;
import com.leo.careerforgeai.interviewflow.application.report.InterviewEvidencePack.TurnEvidence;
import com.leo.careerforgeai.interviewflow.domain.session.InterviewMode;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ComprehensiveInterviewReportTest {
    @Test
    void reportPromptForbidsUsingProfileAsConclusionEvidence() {
        RecordingGateway gateway = new RecordingGateway();
        UUID answerId = UUID.randomUUID();
        gateway.output = "{\"summary\":\"基于回答\",\"strengths\":[],\"improvements\":[],"
                + "\"topicReviews\":[{\"sequenceNo\":1,\"conclusion\":\"有回答依据\","
                + "\"answerIds\":[\"" + answerId + "\"]}]}";
        var engine = new InterviewReportEngine(gateway, JsonMapper.builder().build());
        var evidence = new InterviewEvidencePack(
                UUID.randomUUID(),
                List.of(new TopicEvidence(
                        UUID.randomUUID(),
                        1,
                        "架构",
                        true,
                        List.of(new TurnEvidence(
                                answerId,
                                UUID.randomUUID(),
                                "如何设计？",
                                "我使用事务边界和幂等键完成实现。",
                                "a".repeat(64),
                                "{}")))),
                InterviewMode.COMPREHENSIVE
        );

        engine.generate(evidence);

        assertThat(gateway.request.messages().getFirst().content())
                .contains("确认画像不得作为").contains("冻结回答");
    }

    private static final class RecordingGateway implements ModelGateway {
        String output;
        ModelRequest request;
        @Override public ModelResponse chat(ModelTaskType type, ModelRequest request) {
            this.request = request;
            return new ModelResponse("id", "model", output, null);
        }
        @Override public void stream(ModelTaskType type, ModelRequest request,
                Consumer<ModelStreamEvent> consumer) { throw new AssertionError(); }
    }
}
