package com.leo.careerforgeai.interviewflow.evaluation;

import com.leo.careerforgeai.CareerForgeAiApplication;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.domain.*;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes=CareerForgeAiApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties={"careerforge.persistence.enabled=false","careerforge.auth.enabled=false","spring.flyway.enabled=false","spring.ai.chat.client.enabled=false","spring.ai.model.chat=none"})
@EnabledIfSystemProperty(named="careerforge.quick-interview.ai-smoke",matches="true")
class QuickInterviewAiSmoke {
    @Autowired ModelGateway modelGateway; @Autowired JsonMapper json;
    @Test void returnsStructuredBoundedDecisionAndRetainsAllowedSource() throws Exception {
        String chunk="c".repeat(64);String system="""
                你是受限的面试回合决策组件。只输出JSON，decision只能是FOLLOW_UP或NEXT_TOPIC。
                当回答缺少失败恢复时选择FOLLOW_UP，question必须是一道题，citedChunkIds只能引用输入chunkId。
                格式:{"decision":"FOLLOW_UP","analysis":"...","question":"...","citedChunkIds":["..."]}
                """;
        String input="{\"answer\":\"我直接调用模型，没有考虑超时恢复\",\"candidate\":{\"chunkId\":\""+chunk+"\",\"content\":\"追问模型超时、幂等与任务恢复\"}}";
        long started=System.nanoTime();var response=modelGateway.chat(ModelTaskType.INTERVIEW_TURN_DECISION,
                new ModelRequest(List.of(new ModelMessage(ModelRole.SYSTEM,system),new ModelMessage(ModelRole.USER,input)),ModelOutputFormat.JSON_OBJECT,800,0.0,Duration.ofSeconds(45)));
        var tree=json.readTree(response.content());
        assertThat(tree.get("decision").asText()).isIn("FOLLOW_UP","NEXT_TOPIC");
        if("FOLLOW_UP".equals(tree.get("decision").asText()))assertThat(tree.get("citedChunkIds").toString()).contains(chunk);
        assertThat(Duration.ofNanos(System.nanoTime()-started)).isLessThan(Duration.ofSeconds(60));
    }
}
