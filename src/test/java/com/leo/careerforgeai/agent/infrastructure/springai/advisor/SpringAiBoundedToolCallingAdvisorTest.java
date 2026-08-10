package com.leo.careerforgeai.agent.infrastructure.springai.advisor;

import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.lifecycle.SpringAiToolLoopLimitException;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.lifecycle.SpringAiToolLoopLimitType;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.lifecycle.SpringAiToolRunContext;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证受限Advisor在额外模型调用发生前终止Spring AI默认工具循环。
 * @author: Miao Zheng
 * @date: 2026-08-10 05:20
 **/
class SpringAiBoundedToolCallingAdvisorTest {

    @Test
    @DisplayName("在超过最大模型迭代前终止默认工具循环")
    void shouldStopBeforeModelCallBeyondIterationLimit() {
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        AgentLoopPolicy policy = new AgentLoopPolicy(
                2, 8, 4, 2, 1_000, 100, 10_000,
                Duration.ofSeconds(60), Duration.ofSeconds(30)
        );
        AlwaysToolCallingChatModel chatModel = new AlwaysToolCallingChatModel();
        ChatClient chatClient = ChatClient.builder(
                chatModel,
                ObservationRegistry.NOOP,
                null,
                null,
                SpringAiBoundedToolCallingAdvisor.builder(policy, clock)
        ).build();
        SpringAiToolRunContext runContext = new SpringAiToolRunContext(
                new ToolExecutionContext(
                        "run-1",
                        now.plusSeconds(60),
                        new RetrievalScope("knowledge-base-1", Set.of(), Set.of())
                )
        );

        assertThatThrownBy(() -> chatClient.prompt()
                .user("测试迭代限制")
                .options(DeepSeekChatOptions.builder().maxTokens(100))
                .tools(new NoOpToolCallback())
                .toolContext(runContext.asToolContextMap())
                .call()
                .content())
                .isInstanceOfSatisfying(
                        SpringAiToolLoopLimitException.class,
                        exception -> assertThat(exception.getLimitType())
                                .isEqualTo(SpringAiToolLoopLimitType.MAX_MODEL_ITERATIONS)
                );

        assertThat(chatModel.callCount()).isEqualTo(2);
        assertThat(runContext.modelIterations()).isEqualTo(2);
    }

    /**
     * @program: CareerForge-AI
     * @description: 每次调用都返回工具请求，用于验证Advisor的确定性终止能力。
     * @author: Miao Zheng
     * @date: 2026-08-10 05:20
     **/
    private static final class AlwaysToolCallingChatModel implements ChatModel {

        private int callCount;

        @Override
        public DeepSeekChatOptions getOptions() {
            return DeepSeekChatOptions.builder().build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            callCount++;
            AssistantMessage message = AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                            "provider-call-" + callCount,
                            "function",
                            "test_tool",
                            "{}"
                    )))
                    .build();
            return new ChatResponse(List.of(new Generation(
                    message,
                    ChatGenerationMetadata.builder()
                            .finishReason("tool_calls")
                            .build()
            )));
        }

        private int callCount() {
            return callCount;
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 为Advisor循环边界测试提供无外部依赖的工具回调。
     * @author: Miao Zheng
     * @date: 2026-08-10 05:20
     **/
    private static final class NoOpToolCallback implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return new DefaultToolDefinition(
                    "test_tool",
                    "测试工具",
                    "{\"type\":\"object\",\"properties\":{}}"
            );
        }

        @Override
        public String call(String arguments) {
            return "{\"status\":\"SUCCESS\"}";
        }
    }
}