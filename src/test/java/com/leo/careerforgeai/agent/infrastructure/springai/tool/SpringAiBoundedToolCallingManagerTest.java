package com.leo.careerforgeai.agent.infrastructure.springai.tool;

import com.leo.careerforgeai.agent.application.loop.ToolCallFingerprintService;
import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionContext;
import com.leo.careerforgeai.knowledge.domain.retrieval.RetrievalScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证受限Manager在合法调用时委托默认实现并阻止重复工具调用。
 * @author: Miao Zheng
 * @date: 2026-08-10 06:10
 **/
class SpringAiBoundedToolCallingManagerTest {

    @Test
    @DisplayName("允许前两次等价调用并在第三次重复调用前阻断")
    void shouldDelegateAllowedCallsAndRejectRepeatedCall() {
        AgentLoopPolicy policy = new AgentLoopPolicy(
                6, 8, 4, 2, 1_000, 100, 10_000,
                Duration.ofSeconds(60), Duration.ofSeconds(30)
        );
        RecordingToolCallingManager delegate = new RecordingToolCallingManager();
        SpringAiBoundedToolCallingManager manager = new SpringAiBoundedToolCallingManager(
                delegate,
                policy,
                new ToolCallFingerprintService(JsonMapper.builder().build()),
                "career-coach-v1|prompt=1|tools=1"
        );
        SpringAiToolRunContext runContext = new SpringAiToolRunContext(
                new ToolExecutionContext(
                        "run-1",
                        Instant.parse("2026-08-10T00:01:00Z"),
                        new RetrievalScope("knowledge-base-1", Set.of(), Set.of())
                )
        );
        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .toolContext(runContext.asToolContextMap())
                .build();
        Prompt prompt = new Prompt(new UserMessage("测试重复调用"), options);

        ToolExecutionResult firstResult = manager.executeToolCalls(
                prompt,
                toolCallResponse(
                        "provider-call-1",
                        "{\"query\":\"Java\",\"filters\":{\"b\":2,\"a\":1}}"
                )
        );
        ToolExecutionResult secondResult = manager.executeToolCalls(
                prompt,
                toolCallResponse(
                        "provider-call-2",
                        "{\"filters\":{\"a\":1,\"b\":2},\"query\":\"Java\"}"
                )
        );

        assertThat(firstResult).isSameAs(delegate.result());
        assertThat(secondResult).isSameAs(delegate.result());
        assertThat(delegate.executeCount()).isEqualTo(2);
        assertThat(runContext.totalToolCalls()).isEqualTo(2);

        assertThatThrownBy(() -> manager.executeToolCalls(
                prompt,
                toolCallResponse(
                        "provider-call-3",
                        "{\"query\":\"Java\",\"filters\":{\"a\":1,\"b\":2}}"
                )
        ))
                .isInstanceOfSatisfying(
                        SpringAiToolLoopLimitException.class,
                        exception -> assertThat(exception.getLimitType())
                                .isEqualTo(SpringAiToolLoopLimitType.REPEATED_TOOL_CALL)
                );

        assertThat(delegate.executeCount()).isEqualTo(2);
        assertThat(runContext.totalToolCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("达到工具调用总次数后在执行前阻断")
    void shouldRejectCallBeyondTotalToolCallLimit() {
        AgentLoopPolicy policy = new AgentLoopPolicy(
                6, 2, 2, 2, 1_000, 100, 10_000,
                Duration.ofSeconds(60), Duration.ofSeconds(30)
        );
        RecordingToolCallingManager delegate = new RecordingToolCallingManager();
        SpringAiBoundedToolCallingManager manager = new SpringAiBoundedToolCallingManager(
                delegate,
                policy,
                new ToolCallFingerprintService(JsonMapper.builder().build()),
                "career-coach-v1|prompt=1|tools=1"
        );
        SpringAiToolRunContext runContext = runContext("run-total");
        Prompt prompt = prompt(runContext);

        manager.executeToolCalls(prompt, toolCallResponse(
                "provider-call-1", "search_career_materials", "{\"query\":\"Java\"}"));
        manager.executeToolCalls(prompt, toolCallResponse(
                "provider-call-2", "parse_job_requirements", "{\"jobDescription\":\"Java岗位\"}"));

        assertThatThrownBy(() -> manager.executeToolCalls(
                prompt,
                toolCallResponse(
                        "provider-call-3",
                        "search_career_materials",
                        "{\"query\":\"Spring AI\"}"
                )
        ))
                .isInstanceOfSatisfying(
                        SpringAiToolLoopLimitException.class,
                        exception -> assertThat(exception.getLimitType())
                                .isEqualTo(SpringAiToolLoopLimitType.MAX_TOTAL_TOOL_CALLS)
                );

        assertThat(delegate.executeCount()).isEqualTo(2);
        assertThat(runContext.totalToolCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("达到单工具调用次数后在执行前阻断")
    void shouldRejectCallBeyondPerToolLimit() {
        AgentLoopPolicy policy = new AgentLoopPolicy(
                6, 4, 2, 2, 1_000, 100, 10_000,
                Duration.ofSeconds(60), Duration.ofSeconds(30)
        );
        RecordingToolCallingManager delegate = new RecordingToolCallingManager();
        SpringAiBoundedToolCallingManager manager = new SpringAiBoundedToolCallingManager(
                delegate,
                policy,
                new ToolCallFingerprintService(JsonMapper.builder().build()),
                "career-coach-v1|prompt=1|tools=1"
        );
        SpringAiToolRunContext runContext = runContext("run-per-tool");
        Prompt prompt = prompt(runContext);

        manager.executeToolCalls(prompt, toolCallResponse(
                "provider-call-1", "search_career_materials", "{\"query\":\"Java\"}"));
        manager.executeToolCalls(prompt, toolCallResponse(
                "provider-call-2", "search_career_materials", "{\"query\":\"Spring\"}"));

        assertThatThrownBy(() -> manager.executeToolCalls(
                prompt,
                toolCallResponse(
                        "provider-call-3",
                        "search_career_materials",
                        "{\"query\":\"Redis\"}"
                )
        ))
                .isInstanceOfSatisfying(
                        SpringAiToolLoopLimitException.class,
                        exception -> assertThat(exception.getLimitType())
                                .isEqualTo(SpringAiToolLoopLimitType.MAX_CALLS_PER_TOOL)
                );

        assertThat(delegate.executeCount()).isEqualTo(2);
        assertThat(runContext.totalToolCalls()).isEqualTo(2);
    }

    private ChatResponse toolCallResponse(String toolCallId, String arguments) {
        return toolCallResponse(
                toolCallId,
                "search_career_materials",
                arguments
        );
    }

    private ChatResponse toolCallResponse(
            String toolCallId,
            String toolName,
            String arguments
    ) {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        toolCallId,
                        "function",
                        toolName,
                        arguments
                )))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private SpringAiToolRunContext runContext(String runId) {
        return new SpringAiToolRunContext(
                new ToolExecutionContext(
                        runId,
                        Instant.parse("2026-08-10T00:01:00Z"),
                        new RetrievalScope("knowledge-base-1", Set.of(), Set.of())
                )
        );
    }

    private Prompt prompt(SpringAiToolRunContext runContext) {
        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .toolContext(runContext.asToolContextMap())
                .build();
        return new Prompt(new UserMessage("测试工具调用限制"), options);
    }

    /**
     * @program: CareerForge-AI
     * @description: 记录委托次数并返回固定ToolExecutionResult的内存默认Manager替身。
     * @author: Miao Zheng
     * @date: 2026-08-10 06:10
     **/
    private static final class RecordingToolCallingManager implements ToolCallingManager {

        private final ToolDefinition definition = new DefaultToolDefinition(
                "search_career_materials",
                "测试职业材料搜索",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}"
        );
        private final ToolExecutionResult result = ToolExecutionResult.builder()
                .conversationHistory(List.of())
                .returnDirect(false)
                .build();
        private int executeCount;

        @Override
        public List<ToolDefinition> resolveToolDefinitions(
                ToolCallingChatOptions chatOptions
        ) {
            return List.of(definition);
        }

        @Override
        public ToolExecutionResult executeToolCalls(
                Prompt prompt,
                ChatResponse chatResponse
        ) {
            executeCount++;
            return result;
        }

        private ToolExecutionResult result() {
            return result;
        }

        private int executeCount() {
            return executeCount;
        }
    }
}