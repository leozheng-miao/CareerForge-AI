package com.leo.careerforgeai.agent.domain.loop;

import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.toolcalling.ToolCallingTextMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证Agent初始消息的系统Prompt位置、历史回答和当前用户消息边界
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
class AgentLoopRequestTest {

    private static final RetrievalScope RETRIEVAL_SCOPE =
            new RetrievalScope("careerforge", Set.of(), Set.of());

    @Test
    void shouldAcceptCompleteHistoryEndingWithCurrentUserMessage() {
        AgentLoopRequest request = createRequest(List.of(
                new ToolCallingTextMessage(ModelRole.SYSTEM, "系统规则"),
                new ToolCallingTextMessage(ModelRole.USER, "什么是乐观锁"),
                new ToolCallingTextMessage(ModelRole.ASSISTANT, "乐观锁通过版本号检测并发更新"),
                new ToolCallingTextMessage(ModelRole.USER, "请给出一个例子")
        ));

        assertThat(request.initialMessages()).hasSize(4);
        assertThat(request.initialMessages().getLast().role()).isEqualTo(ModelRole.USER);
        assertThat(request.initialMessages().getLast().content()).isEqualTo("请给出一个例子");
    }

    @Test
    void shouldRejectHistoryEndingWithAssistantMessage() {
        assertThatThrownBy(() -> createRequest(List.of(
                new ToolCallingTextMessage(ModelRole.SYSTEM, "系统规则"),
                new ToolCallingTextMessage(ModelRole.USER, "什么是乐观锁"),
                new ToolCallingTextMessage(ModelRole.ASSISTANT, "乐观锁通过版本号检测并发更新")
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("最后一条初始消息必须是当前USER消息");
    }

    private AgentLoopRequest createRequest(List<ToolCallingTextMessage> messages) {
        return new AgentLoopRequest(
                messages,
                RETRIEVAL_SCOPE,
                ModelOutputFormat.JSON_OBJECT,
                "career-coach-test-v1"
        );
    }
}